package autotrade.local.actor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.actor.SameManager.CutOffMode;
import autotrade.local.exception.ApplicationException;
import autotrade.local.material.LatestInfo;
import autotrade.local.material.Rate;
import autotrade.local.material.StartMarginMode;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import autotrade.local.utility.WebDriverWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader {

    private static AutoTrader instance;
    private static Path logFile = Paths.get("log", "autotrade-local.log");

    private WebDriver driver;
    private WebDriverWrapper wrapper;
    private RateAnalyzer rateAnalyzer;
    private IndicatorManager indicatorManager;
    private UploadManager uploadManager;
    private LotManager lotManager;
    private Messenger messenger;

    private int targetAmountOneTrade;
    private int targetAmountOneDay;
    private int startMargin;

    private LocalDateTime bootDateTime;
    private LocalTime inactiveStart;
    private LocalTime inactiveEnd;

    private long lastFixed;

    private AutoTrader() {
        targetAmountOneTrade = Integer.parseInt(AutoTradeProperties.get("autotrade.targetAmount.oneTrade"));
        targetAmountOneDay = Integer.parseInt(AutoTradeProperties.get("autotrade.targetAmount.oneDay"));

        bootDateTime = LocalDateTime.now();
        inactiveStart = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));

        rateAnalyzer = new RateAnalyzer();
        uploadManager = new UploadManager();
        lotManager = new LotManager();
        messenger = new Messenger(new MessageListener()
                .putCommand(ReservedMessage.LATESTINFO, () -> messenger.set(ReservedMessage.LATESTINFO.name(), AutoTradeUtils.toJson(getLatestInfo())))
                .putCommand(ReservedMessage.UPLOADLOG, () -> uploadManager.upload(logFile))
                .putCommand(ReservedMessage.AUTOTRADELOG, () -> {
                    int logRows = Integer.parseInt(messenger.get("logRows"));
                    try {
                        List<String> lines = Files.readAllLines(logFile);
                        messenger.set(ReservedMessage.AUTOTRADELOG.name(),
                                lines.subList(Math.max(0, lines.size() - logRows), lines.size()).stream().collect(Collectors.joining("\n")));
                    } catch (IOException e) {
                        throw new ApplicationException(e);
                    }
                })
                .putCommand(ReservedMessage.FIXASK, () -> wrapper.fixAsk())
                .putCommand(ReservedMessage.FIXBID, () -> wrapper.fixBid())
                .putCommand(ReservedMessage.FIXALL, () -> wrapper.fixAll())
                .putCommand(ReservedMessage.FORCESAME, () -> this.forceSame())
                );
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
            log.info("indicators {}", indicators);
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

            // 一日の目標金額設定
            targetAmountOneDay = new BigDecimal(startMargin).multiply(new BigDecimal(0.01)).intValue();

            // 繰り返し実行
            while(true) {
                // 取引
                trade();
                AutoTradeUtils.sleep(Duration.ofMillis(100));

                // メッセンジャー再接続
                messenger.reConnect();

                // メッセージダイアログクローズ
                wrapper.cancelMessage();
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            driver.quit();
        }

    }

    private LatestInfo getLatestInfo() {
        return LatestInfo.builder()
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
        LatestInfo latestInfo = getLatestInfo();

        // 次回起動時設定
        startMarginSetting(latestInfo);

        if (isOrderable(latestInfo)) {
            // 最新情報を元に利益確定
            fix(latestInfo);

            // 最新情報を元に注文
            order(latestInfo);
        }

        // analizerにレート追加
        rateAnalyzer.add(latestInfo.getRate());
    }

    private void fix(LatestInfo latestInfo) {

        switch (latestInfo.getStatus()) {
        case NONE:
            SameManager.close();
            break;
        case SAME:
            SameManager.setProfit(latestInfo.getTodaysProfit());
            SameManager sameManager = SameManager.getInstance();

            // 切り離しモード設定
            sameManager.setCutOffMode(CutOffMode.BID);
            if ((latestInfo.getAskAverageRate() + latestInfo.getBidAverageRate()) / 2 < latestInfo.getRate().getAsk()) {
                sameManager.setCutOffMode(CutOffMode.ASK);
            }

            // Ask切り離し判定
            if (sameManager.isCutOffAsk(latestInfo, rateAnalyzer)) {
                // Ask決済
                wrapper.fixAsk();
                log.info("same position recovery start. cut off mode {}, rate {}, ask profit {}, total profit {}",
                        sameManager.getCutOffMode(), latestInfo.getRate(), latestInfo.getAskProfit(), latestInfo.getTotalProfit());

                // 切り離し時点の情報を保存
                sameManager.setAskWhenCutOff(latestInfo.getRate().getAsk());
                sameManager.setBidWhenCutOff(latestInfo.getRate().getBid());
                sameManager.setBidProfitWhenCutOff(latestInfo.getBidProfit());

                // ベリファイ
                verifyOrder(0, LatestInfo::getAskLot);
            }
            // Bid切り離し判定
            if (sameManager.isCutOffBid(latestInfo, rateAnalyzer)) {
                // Bid決済
                wrapper.fixBid();
                log.info("same position recovery start. cut off mode {}, rate {}, bid profit {}, total profit {}",
                        sameManager.getCutOffMode(), latestInfo.getRate(), latestInfo.getBidProfit(), latestInfo.getTotalProfit());

                // 切り離し時点の情報を保存
                sameManager.setAskWhenCutOff(latestInfo.getRate().getAsk());
                sameManager.setBidWhenCutOff(latestInfo.getRate().getBid());
                sameManager.setAskProfitWhenCutOff(latestInfo.getAskProfit());

                // ベリファイ
                verifyOrder(0, LatestInfo::getBidLot);
            }
            break;
        case ASK_SIDE:
        case BID_SIDE:

            // Sameポジション発生後の利益確定判定
            if (SameManager.hasInstance()) {
                // モードを戻す
                SameManager.getInstance().setCutOffMode(CutOffMode.NONE);

                // Sameポジション回復中の場合
                if (SameManager.getInstance().isRecovered(latestInfo.getTotalProfit())) {
                    // Sameポジション回復達成で利益確定
                    wrapper.fixAll();
                    log.info("same position recovery done. rate {}, profit {}, total profit {}", latestInfo.getRate(), latestInfo.getPositionProfit(), latestInfo.getTotalProfit());
                    lastFixed = System.currentTimeMillis();
                }
                return;
            }

            // 通常の利益確定判定
            if (latestInfo.getPositionProfit() >= targetAmountOneTrade) {
                // 目標金額達成で利益確定
                wrapper.fixAll();
                log.info("achieved target amount. rate {}, profit {}, total profit {}", latestInfo.getRate(), latestInfo.getPositionProfit(), latestInfo.getTotalProfit());
                lastFixed = System.currentTimeMillis();
            }
            break;
        default:
        }
    }

    private boolean isOrderable(LatestInfo latestInfo) {
        if (Duration.between(bootDateTime, LocalDateTime.now()).toMillis() < Duration.ofMinutes(3).toMillis()) {
            // 起動直後は注文しない
            return false;
        }
        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(10)) < 20) {
            // 閾値間隔が狭い場合は注文しない
            return false;
        }

        switch (latestInfo.getStatus()) {
        case NONE:
            if (System.currentTimeMillis() - lastFixed < Duration.ofMinutes(1).toMillis()) {
                // 利益確定から１分以内の場合は注文しない
                return false;
            }
            // break無し
        case SAME:
            if (indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(5)) || indicatorManager.isPrevIndicatorWithin(Duration.ofMinutes(5))) {
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
            if (latestInfo.getRate().isWideSpread()) {
                // スプレッドが開いている場合は注文しない
                return false;
            }
            break;
        default:
        }
        return true;
    }

    private void order(LatestInfo latestInfo) {
        Rate rate = latestInfo.getRate();
        if (latestInfo.getTotalProfit() > targetAmountOneDay) {
            // 一日の目標金額を達成した場合は消極的に取引する
            lotManager.modeNegative();
        }

        switch (latestInfo.getStatus()) {
        case NONE:
            // ポジションがない場合
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()) {
                orderAsk(lotManager.getInitialLot());
            }
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()) {
                orderBid(lotManager.getInitialLot());
            }
            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
            // Same前
            if (!SameManager.hasInstance()
                    && rate.getBid() <= rateAnalyzer.getBidThreshold()
                    && rate.getBid() < latestInfo.getAskAverageRate()) {
                // 下値閾値を超えた場合、且つ平均Askレートよりもレートが低い場合
                // 逆ポジション取得
                orderBid(lotManager.nextBidLot(latestInfo));
            }
            // Same後
            if (SameManager.hasInstance()
                    && rate.getBid() <= rateAnalyzer.minWithin(Duration.ofMinutes(1))
                    && rate.getBid() < latestInfo.getAskAverageRate()) {
                // Sameリカバリ中の場合、且つ１分足の下値閾値を超えた場合、且つ平均Askレートよりもレートが低い場合
                // 逆ポジション取得
                orderBid(lotManager.nextBidLot(latestInfo));
            }
            if (SameManager.hasInstance()
                    && rate.getBid() - SameManager.getInstance().getAskWhenCutOff() >= 5) {
                // 小刻みにSame戻し
                orderBid(lotManager.nextBidLot(latestInfo));
                log.info("margin is recovered just a little.");
            }
            break;
        case BID_SIDE:
            // 売りポジションが多い場合
            // Same前
            if (!SameManager.hasInstance()
                    && rateAnalyzer.getAskThreshold() <= rate.getAsk()
                    && latestInfo.getBidAverageRate() < rate.getAsk()) {
                // 上値閾値を超えた場合、且つ平均Bidレートよりもレートが高い場合
                // 逆ポジション取得
                orderAsk(lotManager.nextAskLot(latestInfo));
            }
            // Same後
            if (SameManager.hasInstance()
                    && rateAnalyzer.maxWithin(Duration.ofMinutes(1)) <= rate.getAsk()
                    && latestInfo.getBidAverageRate() < rate.getAsk()) {
                // Sameリカバリ中の場合、且つ１分足の下値閾値を超えた場合、且つ平均Bidレートよりもレートが高い場合
                // 逆ポジション取得
                orderAsk(lotManager.nextAskLot(latestInfo));
            }
            if (SameManager.hasInstance()
                    && SameManager.getInstance().getBidWhenCutOff() - rate.getAsk() >= 5) {
                // 小刻みにSame戻し
                orderBid(lotManager.nextBidLot(latestInfo));
                log.info("margin is recovered just a little.");
            }
            break;
        case SAME:
            // ポジションが同数の場合
            break;
        default:
        }

    }

    private void forceSame() {
        LatestInfo latestInfo = getLatestInfo();
        int askLot = latestInfo.getAskLot();
        int bidLot = latestInfo.getBidLot();
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

    private void orderAsk(int lot) {
        int beforeLot = AutoTradeUtils.toInt(wrapper.getAskLot());
        wrapper.setLot(lot);
        wrapper.orderAsk();
        verifyOrder(beforeLot + lot, LatestInfo::getAskLot);
    }
    private void orderBid(int lot) {
        int beforeLot = AutoTradeUtils.toInt(wrapper.getBidLot());
        wrapper.setLot(lot);
        wrapper.orderBid();
        verifyOrder(beforeLot + lot, LatestInfo::getBidLot);
    }
    private void verifyOrder(int lot, ToIntFunction<LatestInfo> lotAfterOrder) {
        long verifyStarted = System.currentTimeMillis();
        while (true) {
            AutoTradeUtils.sleep(Duration.ofMillis(500));
            LatestInfo latestInfo = getLatestInfo();
            rateAnalyzer.add(latestInfo.getRate());
            if (lot == lotAfterOrder.applyAsInt(latestInfo)) {
                break;
            }
            if (System.currentTimeMillis() - verifyStarted > Duration.ofSeconds(10).toMillis()) {
                throw new ApplicationException("verify is failed.");
            }
        }
    }

    private void startMarginSetting(LatestInfo latestInfo) {
        switch (latestInfo.getStatus()) {
        case NONE:
            if (isInactiveTime()) {
                messenger.set("startMarginMode", StartMarginMode.NEW.name());
            }
            break;
        default:
        }
    }
}
