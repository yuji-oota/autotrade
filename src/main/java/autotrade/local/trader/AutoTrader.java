package autotrade.local.trader;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

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
    private int maxLot;

    private AutoTrader() {
        targetAmount = Integer.parseInt(AutoTradeProperties.get("autotrade.targetAmount"));
        initialLot = Integer.parseInt(AutoTradeProperties.get("autotrade.initialLot"));
        maxLot = Integer.parseInt(AutoTradeProperties.get("autotrade.maxLot"));
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
            log.error(e.getMessage());
        } finally {
            driver.quit();
        }

    }

    private void trade() {

        // ポジション確認
        Position position = Position.builder()
                .askLot(Integer.parseInt(wrapper.getAskLot().replace(",", "")))
                .bidLot(Integer.parseInt(wrapper.getBidLot().replace(",", "")))
                .profit(wrapper.getProfit())
                .build();

        if (position.getProfit() >= targetAmount) {
            // 目標金額達成で利益確定
            wrapper.fixProfit();

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

        if (!position.hasPosition()) {
            // ポジションがない場合
            if (rateAnalyzer.getAskThreshold() - rateAnalyzer.getBidThreshold() < 20) {
                // 閾値間隔が狭い場合は注文しない
                return;
            }
            if (indicateAnalyzer.isNextIndicateWithin(5) ) {
                // 指標が近い場合は注文しない
                return;
            }

            wrapper.setLot(initialLot);
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()) {
                wrapper.orderAsk();
                rateAnalyzer.setLastOrderRate(rate);
            }
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()) {
                wrapper.orderBid();
                rateAnalyzer.setLastOrderRate(rate);
            }
        } else {
            // ポジションがある場合
            if (position.isAskSide()) {
                // 買いポジションが多い場合
                int lot = position.getAskLot() * 2 - position.getBidLot();
                wrapper.setLot(lot > maxLot ? maxLot : lot);
                if (rate.getBid() <= rateAnalyzer.getBidThreshold()
                        && rate.getBid() < rateAnalyzer.getLastOrderRate().getBid()) {
                    // 閾値を超えた場合、且つ前回注文時のBidよりもレートが低い場合
                    // 逆ポジション取得
                    wrapper.orderBid();
                    rateAnalyzer.setLastOrderRate(rate);
                }
            } else {
                // 売りポジションが多い場合
                int lot = position.getBidLot() * 2 - position.getAskLot();
                wrapper.setLot(lot > maxLot ? maxLot : lot);
                if (rateAnalyzer.getAskThreshold() <= rate.getAsk()
                        && rateAnalyzer.getLastOrderRate().getAsk() < rate.getAsk()) {
                    // 閾値を超えた場合、且つ前回注文時のAskよりもレートが高い場合
                    // 逆ポジション取得
                    wrapper.orderAsk();
                    rateAnalyzer.setLastOrderRate(rate);
                }
            }
        }
    }

}
