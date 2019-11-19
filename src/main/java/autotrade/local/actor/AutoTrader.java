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
    private IndicateAnalyzer indicateAnalyzer;
    private UploadManager uploadManager;

    private int targetAmount;
    private int initialLot;
    private int sameLimit;

    private LocalDateTime bootDateTime;
    private LocalTime inactiveStart;
    private LocalTime inactiveEnd;

    private AutoTrader() {
        targetAmount = Integer.parseInt(AutoTradeProperties.get("autotrade.targetAmount"));
        initialLot = Integer.parseInt(AutoTradeProperties.get("autotrade.initialLot"));
        sameLimit = Integer.parseInt(AutoTradeProperties.get("autotrade.sameLimit"));

        bootDateTime = LocalDateTime.now();
        inactiveStart = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));
        rateAnalyzer = new RateAanalyzer();
        uploadManager = new UploadManager();
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
            List<LocalDateTime> indicates = wrapper.getIndicates(LocalDate.now());
            // 翌日分
            indicates.addAll(wrapper.getIndicates(LocalDate.now().plusDays(1)));
            log.info("indicates {}", indicates);
            indicateAnalyzer = new IndicateAnalyzer(indicates);

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

    private void trade() {

        // 最新情報取得
        LatestInfo latestInfo = LatestInfo.builder()
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

        // 通常の利益確定判定
        if (latestInfo.getProfit() >= targetAmount) {
            // 目標金額達成で利益確定
            wrapper.fixAll();
            log.info("achieved target amount. rate {}, profit {}, total profit {}", latestInfo.getRate(), latestInfo.getProfit(), latestInfo.getTotalProfit());
        }

        // Sameポジション発生時の利益確定判定
        switch (latestInfo.getStatus()) {
        case NONE:
            SameManager.close();
            break;
        case SAME:
            SameManager.setProfit(latestInfo.getTodaysProfit());
            break;
        case ASK_SIDE:
        case BID_SIDE:
            if (SameManager.hasInstance()) {
                // Sameポジション回復中の場合
                if (SameManager.getInstance().isRecovered(latestInfo)) {
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
        if (ChronoUnit.MINUTES.between(bootDateTime, LocalDateTime.now()) < 10) {
            // 起動直後は注文しない
            return false;
        }

        switch (latestInfo.getStatus()) {
        case NONE:
        case SAME:
            if (indicateAnalyzer.isNextIndicateWithin(5) || indicateAnalyzer.isPrevIndicateWithin(10) ) {
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

        switch (latestInfo.getStatus()) {
        case NONE:
            // ポジションがない場合
            wrapper.setLot(initialLot);
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()) {
                wrapper.orderAsk();
            }
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()) {
                wrapper.orderBid();
            }
            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
            int bidLot = latestInfo.getAskLot() * 2 - latestInfo.getBidLot();
            wrapper.setLot(latestInfo.getAskLot() >= sameLimit ? sameLimit - latestInfo.getBidLot() : bidLot);
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()
                    && rate.getBid() < latestInfo.getAskAverageRate()) {
                // 下値閾値を超えた場合、且つ平均Askレートよりもレートが低い場合
                // 逆ポジション取得
                wrapper.orderBid();
            }
            break;
        case BID_SIDE:
            // 売りポジションが多い場合
            int askLot = latestInfo.getBidLot() * 2 - latestInfo.getAskLot();
            wrapper.setLot(latestInfo.getBidLot() >= sameLimit ? sameLimit - latestInfo.getAskLot() : askLot);
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()
                    && latestInfo.getBidAverageRate() < rate.getAsk()) {
                // 上値閾値を超えた場合、且つ平均Bidレートよりもレートが高い場合
                // 逆ポジション取得
                wrapper.orderAsk();
            }
            break;
        case SAME:
            // ポジションが同数の場合
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
        default:
        }

    }

    private boolean isInactiveTime() {
        return inactiveStart.isBefore(LocalTime.now()) && LocalTime.now().isBefore(inactiveEnd);
    }

}
