package autotrade.local.utility;

import java.util.List;

import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Indicator;

public interface WebDriverWrapper {

    List<Indicator> getIndicators();

    void login();

    void cancelMessage();

    void startUpTradeTool();

    void orderSettings();

    void pairSettings();

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

    String getBidRateFromList(CurrencyPair pair);

    String getAskRate();

    String getAskRateFromList(CurrencyPair pair);

    String getRateDiffFromList(CurrencyPair pair);

    void setLot(int lot);

    void orderAsk();

    void orderBid();

    void fixAll();

    void fixAsk();

    void fixBid();

    void displayRateList();

    void displayChart();

    void changePair(String pair);

}