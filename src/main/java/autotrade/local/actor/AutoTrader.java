package autotrade.local.actor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.exception.ApplicationException;
import autotrade.local.material.AudioPath;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.PositionStatus;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.material.StartMarginMode;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import autotrade.local.utility.WebDriverWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader {

    private static AutoTrader instance;
    private static Path logFile;
    static {
        logFile = Paths.get("log", "autotrade-local.log");
    }

    private CurrencyPair pair;

    private WebDriver driver;
    private WebDriverWrapper wrapper;
    private RateAnalyzer rateAnalyzer;
    private IndicatorManager indicatorManager;
    private UploadManager uploadManager;
    private LotManager lotManager;
    private Messenger messenger;

    private int targetAmountOneTrade;
    private int targetAmountOneDay;
    private int stopLossAmountOneTrade;
    private int startMargin;

    private LocalDateTime bootDateTime;
    private LocalTime inactiveStart;
    private LocalTime inactiveEnd;

    private long lastFixed;

    private AutoTrader() {
        pair = CurrencyPair.USDJPY;

        targetAmountOneTrade = AutoTradeProperties.getInt("autotrade.targetAmount.oneTrade");
        targetAmountOneDay = AutoTradeProperties.getInt("autotrade.targetAmount.oneDay");
        stopLossAmountOneTrade = AutoTradeProperties.getInt("autotrade.stopLossAmount.oneTrade");

        bootDateTime = LocalDateTime.now();
        inactiveStart = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));

        rateAnalyzer = new RateAnalyzer();
        uploadManager = new UploadManager();
        lotManager = new LotManager();
        messenger = new Messenger(customizeMessageListener());
    }

    public static AutoTrader getInstance() {
        if (Objects.isNull(instance)) {
            instance = new AutoTrader();
        }
        return instance;
    }

    public void operation() {

        try {
            // WebDriver初期化
            driver = new ChromeDriver();
            wrapper = new WebDriverWrapper(driver);

            // 指標を確認する
            // 本日分
            List<LocalDateTime> indicators = wrapper.getIndicators(LocalDate.now());
            // 翌日分
            indicators.addAll(wrapper.getIndicators(LocalDate.now().plusDays(1)));
            log.info("indicators is get.");
            AutoTradeUtils.printObject(indicators);
            indicatorManager = new IndicatorManager(indicators);

            // ログイン
            wrapper.login();
            AutoTradeUtils.sleep(Duration.ofSeconds(5));

            // メッセージダイアログクローズ
            wrapper.cancelMessage();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // ツール起動
            wrapper.startUpTradeTool();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // 設定
            wrapper.settings();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // 開始時の証拠金を取得
            switch (StartMarginMode.valueOf(messenger.get("startMarginMode"))) {
            case NEW:
                startMargin = AutoTradeUtils.toInt(wrapper.getMargin());
                break;
            default:
                startMargin = Integer.parseInt(messenger.get("startMargin"));
                break;
            }
            messenger.set("startMargin", String.valueOf(startMargin));
            messenger.set("startMarginMode", StartMarginMode.CARRY_OVER.name());

            // Same引継ぎ
            Snapshot shapshot = getSnapshot();
            if (shapshot.getStatus() == PositionStatus.SAME) {
                log.info("load Snapshot when samed to SameManager.");
                SameManager.setSnapshot(AutoTradeUtils.deserialize(Base64.getDecoder().decode(messenger.get("snapshotWhenSamed"))));
            }
            // 反対売買閾値引継ぎ
            if (shapshot.hasPosition()) {
                log.info("load countertrading threshold when order to RateAnalyzer.");
                rateAnalyzer.loadCountertradingThreshold(messenger);
            }

            // 繰り返し実行
            while(true) {
                // 取引
                trade();
                AutoTradeUtils.sleep(Duration.ofMillis(100));

                // メッセージダイアログクローズ
                wrapper.cancelMessage();
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            driver.quit();
        }

    }

    private Snapshot getSnapshot() {
        return Snapshot.builder()
                .askLot(AutoTradeUtils.toInt(wrapper.getAskLot()))
                .bidLot(AutoTradeUtils.toInt(wrapper.getBidLot()))
                .askAverageRate(AutoTradeUtils.toInt(wrapper.getAskAverageRate()))
                .bidAverageRate(AutoTradeUtils.toInt(wrapper.getBidAverageRate()))
                .askPipProfit(AutoTradeUtils.toInt(wrapper.getAskPipProfit()))
                .bidPipProfit(AutoTradeUtils.toInt(wrapper.getBidPipProfit()))
                .todaysProfit(AutoTradeUtils.toInt(wrapper.getMargin()) - startMargin)
                .rate(Rate.builder()
                    .ask(AutoTradeUtils.toInt(wrapper.getAskRate()))
                    .bid(AutoTradeUtils.toInt(wrapper.getBidRate()))
                    .timestamp(LocalDateTime.now())
                    .build())
                .build();
    }

    private void trade() {

        // 最新情報取得
        Snapshot snapshot = getSnapshot();

        // 次回起動時設定
        startMarginSetting(snapshot);

        if (isOrderable(snapshot)) {
            // 最新情報を元に利益確定
            fix(snapshot);

            // 最新情報を元に注文
            order(snapshot);
        }

        // analizerにレート追加
        rateAnalyzer.add(snapshot.getRate());

        // 指標アラート
        if (indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(1))
                && !indicatorManager.isNextIndicatorWithin(Duration.ofSeconds(55))) {
            AutoTradeUtils.playAudio(AudioPath.IndicatorAlert);
        }

    }

    private void fix(Snapshot snapshot) {

        switch (snapshot.getStatus()) {
        case NONE:
            SameManager.close();
            break;
        case SAME:
            if (!SameManager.hasInstance()) {
                // Snapshotを保存
                messenger.set("snapshotWhenSamed", Base64.getEncoder().encodeToString(AutoTradeUtils.serialize(snapshot)));
                log.info("save Snapshot when samed.");
                AutoTradeUtils.printObject(snapshot);
            }
            SameManager.setSnapshot(snapshot);
            SameManager sameManager = SameManager.getInstance();

            // Ask切り離し判定
            if (sameManager.isCutOffAsk(snapshot, rateAnalyzer)) {
                // Ask決済
                wrapper.fixAsk();
                log.info("same position recovery start. cut off ask.");
                AutoTradeUtils.printObject(snapshot);

                // ベリファイ
                verifyOrder(0, Snapshot::getAskLot);
            }
            // Bid切り離し判定
            if (sameManager.isCutOffBid(snapshot, rateAnalyzer)) {
                // Bid決済
                wrapper.fixBid();
                log.info("same position recovery start. cut off bid.");
                AutoTradeUtils.printObject(snapshot);

                // ベリファイ
                verifyOrder(0, Snapshot::getBidLot);
            }
            break;
        case ASK_SIDE:
        case BID_SIDE:

            // Sameポジション発生後の利益確定判定
            if (SameManager.hasInstance()) {

                // Sameポジション回復中の場合
                if (SameManager.getInstance().isRecovered(snapshot)) {
                    // Sameポジション回復達成で利益確定
                    fixAll(snapshot);
                }
                return;
            }

            // 通常の利益確定判定
            int targetAmount = targetAmountOneTrade;
            if (lotManager.isNegative()) {
                targetAmount = targetAmountOneTrade / 10;
            }
            if (snapshot.getPositionProfit() >= targetAmount) {
                boolean isFix = false;
                if (snapshot.getStatus() == PositionStatus.ASK_SIDE) {
                    isFix = rateAnalyzer.isReachedBidThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
                } else {
                    isFix = rateAnalyzer.isReachedAskThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
                }
                if (isFix) {
                    // 目標金額達成で利益確定
                    log.info("achieved target amount.");
                    fixAll(snapshot);
                    AutoTradeUtils.playAudio(AudioPath.FixProfit);
                }
                return;
            }
//            if (snapshot.hasBothSide()
//                    && snapshot.getStatus() != PositionStatus.SAME
//                    && snapshot.getPositionProfit() >= targetAmount * -1) {
//                // 反対売買による目標金額達成で利益確定
//                log.info("achieved countertrading.");
//                fixAll(snapshot);
//                return;
//            }
//            if (snapshot.getPositionProfit() <= stopLossAmount) {
//                // 損切確定
//                log.info("reached stop loss limit.");
//                fixAll(snapshot);
//            }
            break;
        default:
        }
    }

    private boolean isOrderable(Snapshot snapshot) {
        if (Duration.between(bootDateTime, LocalDateTime.now()).toMillis() < Duration.ofMinutes(3).toMillis()) {
            // 起動直後は注文しない
            return false;
        }
        if (rateAnalyzer.rangeWithin(Duration.ofSeconds(1)) == pair.getMinSpread()) {
            // 動いていない場合は注文しない
            return false;
        }

        switch (snapshot.getStatus()) {
        case NONE:
            if (System.currentTimeMillis() - lastFixed < Duration.ofSeconds(30).toMillis()) {
                // 利益確定から一定時間内の場合は注文しない
                return false;
            }
            // break無し
        case SAME:
            if (rateAnalyzer.rangeWithin(Duration.ofMinutes(10)) < 20) {
                // 閾値間隔が狭い場合は注文しない
                return false;
            }
            if (indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(5)) || indicatorManager.isPrevIndicatorWithin(Duration.ofSeconds(15))) {
                // 指標が近い場合は注文しない
                return false;
            }
            if (isInactiveTime()) {
                // 非活性時間は注文しない
                // 非活性時間の終了までスリープする
                Duration durationToActive = Duration.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now(), inactiveEnd));
                log.info("application will sleep {} minutes, because of inactive time.", durationToActive.toMinutes());
                AutoTradeUtils.sleep(durationToActive);
                return false;
            }
            if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                // スプレッドが開いている場合は注文しない
                return false;
            }
            break;
        case ASK_SIDE:
        case BID_SIDE:
            if (snapshot.getRate().getSpread() > 4) {
                // 暫定措置
                return false;
            }
            break;
        default:
        }
        return true;
    }

    private void order(Snapshot snapshot) {
        Rate rate = snapshot.getRate();
        if (snapshot.getTotalProfit() > targetAmountOneDay) {
            // 一日の目標金額を達成した場合は消極的に取引する
            lotManager.modeNegative();
        }

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(snapshot);
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
            }
            rateAnalyzer.saveCountertradingThreshold(messenger);
            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
//            if (!SameManager.hasInstance()
//                    && rateAnalyzer.isReachedBidThreshold(rate)
//                    && rate.getBid() < snapshot.getAskAverageRate()) {
//                // 下値閾値を超えた場合、且つ平均Askレートよりもレートが低い場合
//                // 逆ポジション取得
//                orderBid(snapshot);
//            }
//            if (!SameManager.hasInstance()
//                    && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))
//                    && rate.getBid() < snapshot.getAskAverageRate()) {
//                // 下値閾値を超えた場合、且つ平均Askレートよりもレートが低い場合
//                // 逆ポジション取得
//                orderBid(snapshot);
//            }
            if (!SameManager.hasInstance()
                    && rateAnalyzer.isReachedCountertradingBid(rate)) {
                // 下値閾値を超えた場合
                // 逆ポジション取得
                orderBid(snapshot);
            }
            // Same後
            if (SameManager.hasInstance()
                    && SameManager.getInstance().isReSameBid(snapshot, rateAnalyzer)) {
                // Same戻し
                orderBid(snapshot);
            }
            break;
        case BID_SIDE:
            // 売りポジションが多い場合
//            if (!SameManager.hasInstance()
//                    && rateAnalyzer.isReachedAskThreshold(rate)
//                    && snapshot.getBidAverageRate() < rate.getAsk()) {
//                // 上値閾値を超えた場合、且つ平均Bidレートよりもレートが高い場合
//                // 逆ポジション取得
//                orderAsk(snapshot);
//            }
//            if (!SameManager.hasInstance()
//                    && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))
//                    && snapshot.getBidAverageRate() < rate.getAsk()) {
//                // 上値閾値を超えた場合、且つ平均Bidレートよりもレートが高い場合
//                // 逆ポジション取得
//                orderAsk(snapshot);
//            }
            if (!SameManager.hasInstance()
                    && rateAnalyzer.isReachedCountertradingAsk(rate)) {
                // 上値閾値を超えた場合
                // 逆ポジション取得
                orderAsk(snapshot);
            }
            // Same後
            if (SameManager.hasInstance()
                    && SameManager.getInstance().isReSameAsk(snapshot, rateAnalyzer)) {
                // Same戻し
                orderAsk(snapshot);
            }
            break;
        case SAME:
            // ポジションが同数の場合
            break;
        default:
        }

    }

    private void forceSame() {
        Snapshot snapshot = getSnapshot();
        int askLot = snapshot.getAskLot();
        int bidLot = snapshot.getBidLot();
        if (bidLot < askLot) {
            orderBid(askLot - bidLot);
        }
        if (askLot < bidLot) {
            orderAsk(bidLot - askLot);
        }
    }

    private boolean isInactiveTime() {
        return inactiveStart.isBefore(LocalTime.now()) && LocalTime.now().isBefore(inactiveEnd);
    }

    private void orderAsk(Snapshot snapshot) {
        int lot = lotManager.nextAskLot(snapshot);
        orderAsk(lot);
        log.info("order ask. lot {}", lot);
        AutoTradeUtils.printObject(snapshot);
    }
    private void orderBid(Snapshot snapshot) {
        int lot = lotManager.nextBidLot(snapshot);
        orderBid(lot);
        log.info("order bid. lot {}", lot);
        AutoTradeUtils.printObject(snapshot);
    }
    private void orderAsk(int lot) {
        int beforeLot = AutoTradeUtils.toInt(wrapper.getAskLot());
        wrapper.setLot(lot);
        wrapper.orderAsk();
        verifyOrder(beforeLot + lot, Snapshot::getAskLot);
    }
    private void orderBid(int lot) {
        int beforeLot = AutoTradeUtils.toInt(wrapper.getBidLot());
        wrapper.setLot(lot);
        wrapper.orderBid();
        verifyOrder(beforeLot + lot, Snapshot::getBidLot);
    }
    private void fixAll(Snapshot snapshot) {
        wrapper.fixAll();
        AutoTradeUtils.printObject(snapshot);
        lastFixed = System.currentTimeMillis();
        // ベリファイ
        verifyOrder(0, Snapshot::getAskLot);
        verifyOrder(0, Snapshot::getBidLot);
    }
    private void verifyOrder(int lot, ToIntFunction<Snapshot> lotAfterOrder) {
        long verifyStarted = System.currentTimeMillis();
        while (true) {
            AutoTradeUtils.sleep(Duration.ofMillis(500));
            Snapshot snapshot = getSnapshot();
            rateAnalyzer.add(snapshot.getRate());
            if (lot == lotAfterOrder.applyAsInt(snapshot)) {
                break;
            }
            if (System.currentTimeMillis() - verifyStarted > Duration.ofSeconds(10).toMillis()) {
                throw new ApplicationException("verify is failed.");
            }
        }
    }

    private void startMarginSetting(Snapshot snapshot) {
        switch (snapshot.getStatus()) {
        case NONE:
            if (isInactiveTime()) {
                messenger.set("startMarginMode", StartMarginMode.NEW.name());
            }
            break;
        default:
        }
    }

    private MessageListener customizeMessageListener() {
        return new MessageListener()
                .putCommand(ReservedMessage.SNAPSHOT, (args) -> messenger.set(ReservedMessage.SNAPSHOT.name(), AutoTradeUtils.toJson(getSnapshot())))
                .putCommand(ReservedMessage.UPLOADLOG, (args) -> uploadManager.upload(logFile))
                .putCommand(ReservedMessage.AUTOTRADELOG, (args) -> {
                    int logRows = 30;
                    if (args.length > 0) {
                        logRows = Integer.parseInt(args[0]);
                    }
                    List<String> lines = new ArrayList<>();
                    try {
                        lines = Files.readAllLines(logFile);
                    } catch (IOException e) {
                        throw new ApplicationException(e);
                    }
                    if (args.length > 1) {
                        lines = lines.stream().filter(s -> s.contains(args[1])).collect(Collectors.toList());
                    }
                    messenger.set(ReservedMessage.AUTOTRADELOG.name(),
                            lines.subList(Math.max(0, lines.size() - logRows), lines.size()).stream().collect(Collectors.joining("\n")));
                })
                .putCommand(ReservedMessage.FIXASK, (args) -> wrapper.fixAsk())
                .putCommand(ReservedMessage.FIXBID, (args) -> wrapper.fixBid())
                .putCommand(ReservedMessage.FIXALL, (args) -> wrapper.fixAll())
                .putCommand(ReservedMessage.FORCESAME, (args) -> this.forceSame())
                .putCommand(ReservedMessage.LOTPOSITIVE, (args) -> lotManager.modePositive())
                .putCommand(ReservedMessage.LOTNEGATIVE, (args) -> lotManager.modeNegative())
                ;
    }

}
