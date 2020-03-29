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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.exception.ApplicationException;
import autotrade.local.material.AudioPath;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.material.StartMarginMode;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import autotrade.local.utility.WebDriverWrapper;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader {

    private static AutoTrader instance;
    private static Path logFile;
    static {
        logFile = Paths.get("log", "autotrade-local.log");
    }

    private CurrencyPair pair;
    private DisplayMode displayMode;

    private WebDriver driver;
    private WebDriverWrapper wrapper;
    private Map<CurrencyPair, RateAnalyzer> pairRateMap;
    private RateAnalyzer rateAnalyzer;
    private IndicatorManager indicatorManager;
    private UploadManager uploadManager;
    private LotManager lotManager;

    @SuppressWarnings("unused")
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

    private int targetAmountOneDay;
    private int startMargin;

    private LocalTime inactiveStart;
    private LocalTime inactiveEnd;

    private long lastFixed;

    private boolean isThroughOrder;
    private boolean isThroughFix;
    private boolean isIgnoreSpread;

    private AutoTrader() {
        pair = CurrencyPair.USDJPY;
        displayMode = DisplayMode.CHART;

        targetAmountOneDay = AutoTradeProperties.getInt("autotrade.targetAmount.oneDay");
        inactiveStart = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));

        pairRateMap = Stream.of(CurrencyPair.values()).collect(Collectors.toMap(pair -> pair, pair -> new RateAnalyzer()));
        rateAnalyzer = pairRateMap.get(pair);
        uploadManager = new UploadManager();
        lotManager = new LotManager();
        indicatorManager = new IndicatorManager();
        pubSubConnection = Messenger.createPubSubConnection(customizeMessageListener());
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
            if (!indicatorManager.hasIndicator()) {
                // 本日分
                indicatorManager.addIndicators(wrapper.getIndicators(LocalDate.now()));
                // 翌日分
                indicatorManager.addIndicators(wrapper.getIndicators(LocalDate.now().plusDays(1)));
                log.info("indicators is got.");
                AutoTradeUtils.printObject(indicatorManager.getIndicators());
            }

            // ログイン
            wrapper.login();
            AutoTradeUtils.sleep(Duration.ofSeconds(5));

            // メッセージダイアログクローズ
            wrapper.cancelMessage();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // ツール起動
            wrapper.startUpTradeTool();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // 取引設定
            wrapper.orderSettings();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // 通貨ペア設定
            wrapper.pairSettings();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // 通貨ペア変更
            changePair(pair);

            // 開始時の証拠金を取得
            switch (StartMarginMode.valueOf(Messenger.get("startMarginMode"))) {
            case NEW:
                startMargin = AutoTradeUtils.toInt(wrapper.getMargin());
                break;
            default:
                startMargin = Integer.parseInt(Messenger.get("startMargin"));
                break;
            }
            Messenger.set("startMargin", String.valueOf(startMargin));
            Messenger.set("startMarginMode", StartMarginMode.CARRY_OVER.name());

            // Same引継ぎ
            Snapshot shapshot = getSnapshot();
            if (shapshot.isPositionSame()) {
                log.info("load Snapshot when samed to SameManager.");
                SameManager.setSnapshot(AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("snapshotWhenSamed"))));
            }
            // 反対売買閾値引継ぎ
            if (shapshot.hasPosition()) {
                log.info("load countertrading threshold when order to RateAnalyzer.");
                rateAnalyzer.loadCountertradingThreshold();
            }

            // 繰り返し実行
            while(true) {
                // 取引
                trade();

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
                .pair(wrapper.getPair())
                .askLot(AutoTradeUtils.toInt(wrapper.getAskLot()))
                .bidLot(AutoTradeUtils.toInt(wrapper.getBidLot()))
                .askAverageRate(AutoTradeUtils.toInt(wrapper.getAskAverageRate()))
                .bidAverageRate(AutoTradeUtils.toInt(wrapper.getBidAverageRate()))
                .askPipProfit(AutoTradeUtils.toInt(wrapper.getAskPipProfit()))
                .bidPipProfit(AutoTradeUtils.toInt(wrapper.getBidPipProfit()))
                .margin(AutoTradeUtils.toInt(wrapper.getMargin()))
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

        if (isOrderable(snapshot)) {

            // 確定見送り判定
            if (!isThroughFix) {
                // 最新情報を元に利益確定
                fix(snapshot);
            }

            // 注文見送り判定
            if (!isThroughOrder) {
                // 最新情報を元に注文
                order(snapshot);
            }
        }

        // rateAnalyzerにレート追加
        rateAnalyzer.add(snapshot.getRate());

        // 選択可能通貨のrateAnalyzerにレート追加
        if (displayMode == DisplayMode.RATELIST) {
            Stream.of(CurrencyPair.values()).forEach(p -> {
                if (p == pair) {
                    return;
                }
                pairRateMap.get(p).add(Rate.builder()
                        .ask(AutoTradeUtils.toInt(wrapper.getAskRateFromList(p)))
                        .bid(AutoTradeUtils.toInt(wrapper.getBidRateFromList(p)))
                        .timestamp(LocalDateTime.now())
                        .build());
            });
        }

        // 指標アラート
        if (indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(1))
                && !indicatorManager.isNextIndicatorWithin(Duration.ofSeconds(55))) {
            AutoTradeUtils.playAudioRandom(AudioPath.Alert);
        }

        // 非活性時間処理
        if (isInactiveTime()) {
            if (snapshot.isPositionNone()
                    && pair.getMinSpread() < snapshot.getRate().getSpread()) {
                // ポジションが無く、スプレッドが最小よりも広がった場合

                // 次回起動時設定
                Messenger.set("startMarginMode", StartMarginMode.NEW.name());

                // 非活性時間の終了までスリープする
                Duration durationToActive = Duration.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now(), inactiveEnd));
                log.info("application will sleep {} minutes, because of inactive time.", durationToActive.toMinutes());
                AutoTradeUtils.sleep(durationToActive);
            }
        }

    }

    private void fix(Snapshot snapshot) {

        switch (snapshot.getStatus()) {
        case NONE:
            SameManager.close();
            break;
        case SAME:
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
            if (isFixBeforeSame(snapshot, lotManager.getInitial() * 10)) {
                // 目標金額達成で利益確定
                log.info("achieved target amount.");
                fixAll(snapshot);
                return;
            }
            if (snapshot.hasBothSide()
                    && isFixBeforeSame(snapshot, 0)) {
                // 反対売買による目標金額達成で利益確定
                log.info("achieved countertrading.");
                fixAll(snapshot);
                return;
            }
            break;
        default:
        }
    }

    private boolean isFixBeforeSame(Snapshot snapshot, int targetAmount) {
        if (snapshot.getPositionProfit() >= targetAmount) {
            if (snapshot.isPositionAskSide()) {
                return rateAnalyzer.isReachedBidThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
            }
            if (snapshot.isPositionBidSide()) {
                return rateAnalyzer.isReachedAskThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
            }
        }
        return false;
    }

    private boolean isOrderable(Snapshot snapshot) {
        if (Duration.between(rateAnalyzer.getEarliestRate().getTimestamp()
                , LocalDateTime.now()).toMillis() < Duration.ofMinutes(5).toMillis()) {
            // 過去Rateがある程度存在しない場合は注文しない
            return false;
        }
        if (rateAnalyzer.rangeWithin(Duration.ofSeconds(1)) == pair.getMinSpread()) {
            // 動いていない場合は注文しない
            return false;
        }
        if (snapshot.getRate().isDoubtful()) {
            // スプレッドが開きすぎの場合は注文しない
            return false;
        }

        switch (snapshot.getStatus()) {
        case NONE:
            if (System.currentTimeMillis() - lastFixed < Duration.ofSeconds(10).toMillis()) {
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
            if (!isIgnoreSpread && pair.getMinSpread() < snapshot.getRate().getSpread()) {
                // スプレッドを無視しない
                // 且つスプレッドが開いている場合は注文しない
                return false;
            }
            break;
        case ASK_SIDE:
        case BID_SIDE:
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
                rateAnalyzer.saveCountertradingThreshold();
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
                rateAnalyzer.saveCountertradingThreshold();
            }
            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
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
        int lot = lotManager.nextLot(snapshot);
        orderAsk(lot);
        log.info("order ask. lot {}", lot);
        AutoTradeUtils.printObject(snapshot);
        AutoTradeUtils.playAudioRandom(AudioPath.OrderSoundEffect);
    }
    private void orderBid(Snapshot snapshot) {
        int lot = lotManager.nextLot(snapshot);
        orderBid(lot);
        log.info("order bid. lot {}", lot);
        AutoTradeUtils.printObject(snapshot);
        AutoTradeUtils.playAudioRandom(AudioPath.OrderSoundEffect);
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
        AutoTradeUtils.playAudioRandom(AudioPath.FixSoundEffect);
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
    private void displayRateList() {
        this.displayMode = DisplayMode.RATELIST;
        wrapper.displayRateList();
    }
    private void displayChart() {
        this.displayMode = DisplayMode.CHART;
        wrapper.displayChart();
    }
    private void changePair(CurrencyPair pair) {
        if (this.getSnapshot().hasPosition()) {
            log.info("currency pair is not changed because of position exists.");
            return;
        }
        if (this.pair == pair) {
            log.info("currency pair setting is already set {}.", this.pair.getDescription());
            return;
        }
        this.displayRateList();
        boolean saved = this.isThroughOrder;
        this.isThroughOrder = true;
        this.pair = pair;
        wrapper.changePair(this.pair.getDescription());
        this.rateAnalyzer = this.pairRateMap.get(this.pair);
        this.isThroughOrder = saved;
        log.info("currency pair setting is set {}.", this.pair.getDescription());
    }
    private void changeRecommended() {
        if (this.displayMode != DisplayMode.RATELIST) {
            log.info("change recommended is executable when display mode RATELIST.");
            return;
        }
        CurrencyPair recommended = this.pairRateMap.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().rangeWithin(Duration.ofMinutes(10))))
                .get()
                .getKey();
        this.changePair(recommended);
    }

    private MessageListener customizeMessageListener() {
        return new MessageListener()
                .putCommand(ReservedMessage.SNAPSHOT, (args) -> Messenger.set(ReservedMessage.SNAPSHOT.name(), AutoTradeUtils.toJson(getSnapshot())))
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
                    Messenger.set(ReservedMessage.AUTOTRADELOG.name(),
                            lines.subList(Math.max(0, lines.size() - logRows), lines.size()).stream().collect(Collectors.joining("\n")));
                })
                .putCommand(ReservedMessage.FIXASK, (args) -> wrapper.fixAsk())
                .putCommand(ReservedMessage.FIXBID, (args) -> wrapper.fixBid())
                .putCommand(ReservedMessage.FIXALL, (args) -> wrapper.fixAll())
                .putCommand(ReservedMessage.FORCESAME, (args) -> this.forceSame())
                .putCommand(ReservedMessage.FORCEASK, (args) -> this.orderAsk(this.getSnapshot()))
                .putCommand(ReservedMessage.FORCEBID, (args) -> this.orderBid(this.getSnapshot()))
                .putCommand(ReservedMessage.LOTNEGATIVE, (args) -> lotManager.modeNegative())
                .putCommand(ReservedMessage.LOTPOSITIVE, (args) -> lotManager.modePositive())
                .putCommand(ReservedMessage.LOTPOSITIVEINCREMENT, (args) -> lotManager.incrementInitialPositive())
                .putCommand(ReservedMessage.LOTPOSITIVEDECREMENT, (args) -> lotManager.decrementInitialPositive())
                .putCommand(ReservedMessage.THROUGHORDER, (args) -> {
                    if (args.length > 0) {
                        this.isThroughOrder = Boolean.valueOf(args[0]);
                    }
                    log.info("through order setting is set {}.", this.isThroughOrder);
                })
                .putCommand(ReservedMessage.THROUGHFIX, (args) -> {
                    if (args.length > 0) {
                        this.isThroughFix = Boolean.valueOf(args[0]);
                    }
                    log.info("through fix setting is set {}.", this.isThroughFix);
                })
                .putCommand(ReservedMessage.IGNORESPREAD, (args) -> {
                    if (args.length > 0) {
                        this.isIgnoreSpread = Boolean.valueOf(args[0]);
                    }
                    log.info("ignore spread setting is set {}.", this.isIgnoreSpread);
                })
                .putCommand(ReservedMessage.SAVECOUNTERTRADINGTHRESHOLD, (args) -> rateAnalyzer.saveCountertradingThreshold())
                .putCommand(ReservedMessage.CHANGEPAIR, (args) -> {
                    if (args.length > 0) {
                        this.changePair(CurrencyPair.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.CHANGERECOMMENDED, (args) -> this.changeRecommended())
                .putCommand(ReservedMessage.DISPLAYCHART, (args) -> this.displayChart())
                .putCommand(ReservedMessage.DISPLAYRATELIST, (args) -> this.displayRateList())
                ;
    }

}
