package autotrade.local.utility;

import java.util.List;

import org.openqa.selenium.WebDriver;

import autotrade.local.material.Indicator;
import autotrade.local.material.Pair;

public interface WebDriverWrapper {

    List<Indicator> getIndicators();

    void setDriver(WebDriver driver);

    void initialize();

    String getPair();

    String getMargin();

    String getEffectiveMargin();

    String getAskLot();

    String getBidLot();

    String getAskAverageRate();

    String getBidAverageRate();

    String getAskPipProfit();

    String getBidPipProfit();

    String getBidRate();

    String getBidRateFromList(Pair pair);

    String getAskRate();

    String getAskRateFromList(Pair pair);

    String getRateDiffFromList(Pair pair);

    void setLot(int lot);

    void orderAsk();

    void orderBid();

    void fixAll();

    void fixAsk();

    void fixBid();

    void displayRateList();

    void displayChart();

    void changePair(String pair);

    void quit();

}