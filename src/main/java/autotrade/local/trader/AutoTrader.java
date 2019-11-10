package autotrade.local.trader;

import java.time.LocalDateTime;
import java.util.Objects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class AutoTrader {

    private static AutoTrader instance;

    private WebDriver driver;
    private WebDriverWrapper wrapper;
    private RateAanalyzer rateAnalyzer;

    private int targetAmount;
    private int initialLot;

    private AutoTrader() {
        targetAmount = 100;
        initialLot = 1;
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
//            System.setProperty("webdriver.chrome.driver", AutoTradeProperties.get("webdriver.chrome.driver"));
            driver = new ChromeDriver();
            wrapper = new WebDriverWrapper(driver);

            wrapper.login();
            wrapper.startUpTradeTool();
            Thread.sleep(15000);

            // 繰り返し実行
            while(true) {
                trade();
                Thread.sleep(1000);
            }
        } catch(Exception e) {
            e.printStackTrace();
        } finally {
            try {
                driver.quit();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

    }

    private void trade() {

        // ポジション確認
        Position position = Position.builder()
                .askLot(Integer.parseInt(wrapper.getAskLot().replace(",", "")))
                .bidLot(Integer.parseInt(wrapper.getBidLot().replace(",", "")))
                .profit(Integer.parseInt(wrapper.getProfit().replace(",", "")))
                .build();

        if (position.getProfit() >= targetAmount) {
            // 目標金額達成で利益確定
            wrapper.allPayments();

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
        if (rateAnalyzer.getAskThreshold() - rateAnalyzer.getBidThreshold() < 10) {
            // 閾値間隔が狭い場合は注文しない
            return;
        }

        if (!position.hasPosition()) {
            // ポジションがない場合
            wrapper.setLot(initialLot);
            if (rateAnalyzer.getAskThreshold() <= rate.getAsk()) {
                wrapper.orderAsk();
            }
            if (rate.getBid() <= rateAnalyzer.getBidThreshold()) {
                wrapper.orderBid();
            }
        } else {
            // ポジションがある場合
            if (position.isAskSide()) {
                // 買いポジションが多い場合
                wrapper.setLot(position.getAskLot() * 2 - position.getBidLot());
                if (rate.getBid() <= rateAnalyzer.getBidThreshold()) {
                    // 閾値を超えた場合
                    // 逆ポジション取得
                    wrapper.orderBid();
                }
            } else {
                // 売りポジションが多い場合
                wrapper.setLot(position.getBidLot() * 2 - position.getAskLot());
                if (rateAnalyzer.getAskThreshold() <= rate.getAsk()) {
                    // 閾値を超えた場合
                    // 逆ポジション取得
                    wrapper.orderAsk();
                }
            }
        }
    }

}
