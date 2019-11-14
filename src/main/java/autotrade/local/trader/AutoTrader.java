package autotrade.local.trader;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader {

    private static AutoTrader instance;

    private WebDriver driver;
    private WebDriverWrapper wrapper;
    private RateAanalyzer rateAnalyzer;
    private IndicateAnalyzer indicateAnalyzer;

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

            // ツール起動
            wrapper.startUpTradeTool();
            Thread.sleep(15000);

            // 設定
            wrapper.settings();

            // 繰り返し実行
            while(true) {
                trade();
                Thread.sleep(1000);
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            driver.quit();
        }

    }

    private void trade() {

        // ポジション確認
        Position position = Position.builder()
                .askLot(Integer.parseInt(wrapper.getAskLot().replace(",", "")))
                .bidLot(Integer.parseInt(wrapper.getBidLot().replace(",", "")))
                .askAverageRate(Integer.parseInt(wrapper.getAskAverageRate().replace(".", "")))
                .bidAverageRate(Integer.parseInt(wrapper.getBidAverageRate().replace(".", "")))
                .askProfit(Integer.parseInt(wrapper.getAskProfit().replace(",", "")))
                .bidProfit(Integer.parseInt(wrapper.getBidProfit().replace(",", "")))
                .build();

        if (position.getProfit() >= targetAmount) {
            // 目標金額達成で利益確定
            wrapper.fixAll();
            log.info("profit {}", position.getProfit());

            // ポジション初期化
            position = Position.builder().build();
        }

        // レート取得
        Rate rate = Rate.builder()
                .ask(Integer.parseInt(wrapper.getAskRate().replace(".", "")))
                .bid(Integer.parseInt(wrapper.getBidRate().replace(".", "")))
                .timestamp(LocalDateTime.now())
                .build();

        // 注文
        order(position, rate);

        // analizerにレート追加
        rateAnalyzer.add(rate);
    }

    private void order(Position position, Rate rate) {

        if (ChronoUnit.MINUTES.between(bootDateTime, LocalDateTime.now()) < 10) {
            // 起動直後は注文しない
            return;
        }

        if (rateAnalyzer.getAskThreshold() - rateAnalyzer.getBidThreshold() < 20) {
            // 閾値間隔が狭い場合は注文しない
            return;
        }
        switch (position.getStatus()) {
        case NONE:
            // ポジションがない場合
            if (indicateAnalyzer.isNextIndicateWithin(5) || indicateAnalyzer.isPrevIndicateWithin(10) ) {
                // 指標が近い場合は注文しない
                return;
            }
            if (isInactiveTime()) {
                // 非活性時間は注文しない
                inactiveProcess();
                return;
            }

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
            int bidLot = position.getAskLot() * 2 - position.getBidLot();
            wrapper.setLot(position.getAskLot() >= sameLimit ? sameLimit - position.getBidLot() : bidLot);
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()
                    && rate.getBid() < position.getAskAverageRate()) {
                // 下値閾値を超えた場合、且つ平均Askレートよりもレートが低い場合
                // 逆ポジション取得
                wrapper.orderBid();
            }
            break;
        case BID_SIDE:
            // 売りポジションが多い場合
            int askLot = position.getBidLot() * 2 - position.getAskLot();
            wrapper.setLot(position.getBidLot() >= sameLimit ? sameLimit - position.getAskLot() : askLot);
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()
                    && position.getBidAverageRate() < rate.getAsk()) {
                // 上値閾値を超えた場合、且つ平均Bidレートよりもレートが高い場合
                // 逆ポジション取得
                wrapper.orderAsk();
            }
            break;
        case SAME:
            // ポジションが同数の場合
            if (rate.getBid() <= rateAnalyzer.getBidThreshold() && position.getAskProfit() > 0) {
                // 下値閾値を超えて利益が出ている場合
                // Ask決済
                wrapper.fixAsk();
                log.info("same position recovery ask profit {}", position.getAskProfit());
            }
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk() && position.getBidProfit() > 0) {
                // 上値閾値を超えて利益が出ている場合
                // Ask決済
                wrapper.fixBid();
                log.info("same position recovery bid profit {}", position.getBidProfit());
            }
            break;
        default:
        }

    }

    private boolean isInactiveTime() {
        return inactiveStart.isBefore(LocalTime.now()) && LocalTime.now().isBefore(inactiveEnd);
    }

    private void inactiveProcess() {
        try {
            Thread.sleep(ChronoUnit.MILLIS.between(LocalDateTime.now(), inactiveEnd));
        } catch (InterruptedException e) {
            throw new ApplicationException(e);
        }
        throw new ApplicationException("forced termination for activate");
    }
}
