package autotrade.local.trader;

import java.util.Objects;

import autotrade.local.exception.ApplicationException;

public class SameManager {

    private static SameManager instance;
    private int todaysProfitWhenSamed;

    private SameManager(int todaysProfit) {
        this.todaysProfitWhenSamed = todaysProfit;
    }

    public static void setProfit(int todaysProfit) {
        if (Objects.isNull(instance)) {
            instance = new SameManager(todaysProfit);
        }
    }
    public static SameManager getInstance() {
        if (Objects.isNull(instance)) {
            throw new ApplicationException("setProfit must be done.");
        }
        return instance;
    }

    public boolean isRecovered(LatestInfo latestInfo) {
        if (latestInfo.getTotalProfit() >= todaysProfitWhenSamed) {
            return true;
        }
        return false;
    }

    public static void close() {
        instance = null;
    }

}
