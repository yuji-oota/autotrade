package autotrade.local.actor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.material.LatestInfo;
import autotrade.local.material.Rate;
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
    private RateAanalyzer rateAnalyzer;
    private IndicatorManager indicatorManager;
    private UploadManager uploadManager;
    private LotManager lotManager;
    private Messenger messenger;

    private int targetAmountOneTrade;
    private int targetAmountOneDay;

    private LocalDateTime bootDateTime;
    private LocalTime inactiveStart;
    private LocalTime inactiveEnd;

    private AutoTrader() {
        targetAmountOneTrade = Integer.parseInt(AutoTradeProperties.get("autotrade.targetAmount.oneTrade"));
        targetAmountOneDay = Integer.parseInt(AutoTradeProperties.get("autotrade.targetAmount.oneDay"));

        bootDateTime = LocalDateTime.now();
        inactiveStart = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));
        rateAnalyzer = new RateAanalyzer();
        uploadManager = new UploadManager();
        lotManager = new LotManager();

        MessageListener listener = new MessageListener();
        listener.putCommand(ReservedMessage.LATESTINFO, v -> messenger.set(ReservedMessage.LATESTINFO.name(), getLatestInfo().toString()));
        listener.putCommand(ReservedMessage.FIXASK, v -> wrapper.fixAsk());
        listener.putCommand(ReservedMessage.FIXBID, v -> wrapper.fixBid());
        listener.putCommand(ReservedMessage.FIXALL, v -> wrapper.fixAll());
        messenger = new Messenger(listener);
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
            Thread.sleep(15000);

            // ツール起動
            wrapper.startUpTradeTool();
            Thread.sleep(15000);

            // 設定
            wrapper.settings();
            Thread.sleep(1000);

            // 繰り返し実行
            while(true) {
                trade();
                // ログファイルアップロード
                uploadManager.upload(logFile);
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            driver.quit();
        }

    }

    private LatestInfo getLatestInfo() {
        return LatestInfo.builder()
                .askLot(Integer.parseInt(wrapper.getAskLot().replace(",", "")))
                .bidLot(Integer.parseInt(wrapper.getBidLot().replace(",", "")))
                .askAverageRate(Integer.parseInt(wrapper.getAskAverageRate().replace(".", "")))
                .bidAverageRate(Integer.parseInt(wrapper.getBidAverageRate().replace(".", "")))
                .askProfit(Integer.parseInt(wrapper.getAskProfit().replace(",", "")))
                .bidProfit(Integer.parseInt(wrapper.getBidProfit().replace(",", "")))
                .todaysProfit(Integer.parseInt(wrapper.getTodaysProfit().replace(",", "")))
                .rate(Rate.builder()
                    .ask(Integer.parseInt(wrapper.getAskRate().replace(".", "")))
                    .bid(Integer.parseInt(wrapper.getBidRate().replace(".", "")))
                    .timestamp(LocalDateTime.now())
                    .build())
                .build();
    }

    private void trade() {

        // 最新情報取得
        LatestInfo latestInfo = getLatestInfo();

        // 最新情報を元に利益確定
        fix(latestInfo);

        if (isOrderable(latestInfo)) {
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

            Rate rate = latestInfo.getRate();
            if (rate.getBid() <= rateAnalyzer.getBidThreshold() && latestInfo.getAskProfit() > 0) {
                // 下値閾値を超えて利益が出ている場合
                // Ask決済
                wrapper.fixAsk();
                log.info("same position recovery start. rate {}, ask profit {}, total profit {}", latestInfo.getRate(), latestInfo.getAskProfit(), latestInfo.getTotalProfit());
            }
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk() && latestInfo.getBidProfit() > 0) {
                // 上値閾値を超えて利益が出ている場合
                // Bid決済
                wrapper.fixBid();
                log.info("same position recovery start. rate {}, bid profit {}, total profit {}", latestInfo.getRate(), latestInfo.getBidProfit(), latestInfo.getTotalProfit());
            }
            break;
        case ASK_SIDE:
        case BID_SIDE:

            // 通常の利益確定判定
            if (latestInfo.getProfit() >= targetAmountOneTrade) {
                // 目標金額達成で利益確定
                wrapper.fixAll();
                log.info("achieved target amount. rate {}, profit {}, total profit {}", latestInfo.getRate(), latestInfo.getProfit(), latestInfo.getTotalProfit());
            }

            // Sameポジション発生後の利益確定判定
            if (SameManager.hasInstance()) {
                // Sameポジション回復中の場合
                if (SameManager.getInstance().isRecovered(latestInfo.getTotalProfit())) {
                    // Sameポジション回復達成で利益確定
                    wrapper.fixAll();
                    log.info("same position recovery done. rate {}, profit {}, total profit {}", latestInfo.getRate(), latestInfo.getProfit(), latestInfo.getTotalProfit());
                }
            }
            break;
        default:
        }
    }

    private boolean isOrderable(LatestInfo latestInfo) {
        if (ChronoUnit.MINUTES.between(bootDateTime, LocalDateTime.now()) < 10) {
            // 起動直後は注文しない
            return false;
        }
        if (rateAnalyzer.rangeWithin(10) < 20) {
            // 閾値間隔が狭い場合は注文しない
            return false;
        }

        switch (latestInfo.getStatus()) {
        case NONE:
        case SAME:
            if (indicatorManager.isNextIndicatorWithin(5) || indicatorManager.isPrevIndicatorWithin(5) ) {
                // 指標が近い場合は注文しない
                return false;
            }
            if (isInactiveTime()) {
                // 非活性時間は注文しない
                // 非活性時間の終了までスリープする
                long minutesToActive = ChronoUnit.MINUTES.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now(), inactiveEnd));
                log.info("application will sleep {} minutes, because of inactive time.", minutesToActive);
                AutoTradeUtils.sleep(TimeUnit.MINUTES.toMillis(minutesToActive));
                return false;
            }
            if (latestInfo.getRate().getAsk() - latestInfo.getRate().getBid() > 3) {
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
            wrapper.setLot(lotManager.getInitialLot());
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()) {
                wrapper.orderAsk();
            }
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()) {
                wrapper.orderBid();
            }
            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
            wrapper.setLot(lotManager.nextBidLot(latestInfo));
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()
                    && rate.getBid() < latestInfo.getAskAverageRate()) {
                // 下値閾値を超えた場合、且つ平均Askレートよりもレートが低い場合
                // 逆ポジション取得
                wrapper.orderBid();
            }
            break;
        case BID_SIDE:
            // 売りポジションが多い場合
            wrapper.setLot(lotManager.nextAskLot(latestInfo));
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()
                    && latestInfo.getBidAverageRate() < rate.getAsk()) {
                // 上値閾値を超えた場合、且つ平均Bidレートよりもレートが高い場合
                // 逆ポジション取得
                wrapper.orderAsk();
            }
            break;
        case SAME:
            // ポジションが同数の場合
            break;
        default:
        }

    }

    private boolean isInactiveTime() {
        return inactiveStart.isBefore(LocalTime.now()) && LocalTime.now().isBefore(inactiveEnd);
    }

}
