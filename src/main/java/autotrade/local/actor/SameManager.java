package autotrade.local.actor;

import java.time.Duration;
import java.util.Objects;

import autotrade.local.exception.ApplicationException;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SameManager {

    private static SameManager instance;
    private int todaysProfitWhenSamed;

    @Getter
    @Setter
    private CutOffMode cutOffMode;

    enum CutOffMode {
        NONE,
        ASK,
        BID,
    }

    @Setter
    @Getter
    private Snapshot shapshotWhenCutOff;

    private SameManager(int todaysProfit) {
        this.todaysProfitWhenSamed = todaysProfit;
        this.cutOffMode = CutOffMode.NONE;
    }

    public static void setProfit(int todaysProfit) {
        if (Objects.isNull(instance)) {
            instance = new SameManager(todaysProfit);
            log.info("same position started. todays profit {}", todaysProfit);
        }
    }
    public static SameManager getInstance() {
        if (Objects.isNull(instance)) {
            throw new ApplicationException("setProfit must be done.");
        }
        return instance;
    }

    public static boolean hasInstance() {
        return Objects.nonNull(instance);
    }

    public boolean isRecovered(int totalProfit) {
        if (totalProfit >= todaysProfitWhenSamed) {
            return true;
        }
        return false;
    }

    public static void close() {
        instance = null;
    }

    public boolean isCutOffAsk(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        if (cutOffMode != CutOffMode.ASK) {
            return false;
        }
        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(5)) < 20) {
            return false;
        }
        Rate rate = snapshot.getRate();
//        if (rate.getBid() <= rateAnalyzer.getBidThreshold()) {
//            return true;
//        }
//        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(10)) > 30 && rate.getBid() <= rateAnalyzer.minWithin(Duration.ofMinutes(1))) {
//            return true;
//        }
        if (rate.getBid() <= rateAnalyzer.minWithin(Duration.ofMinutes(1))) {
            return true;
        }
        return false;
    }

    public boolean isCutOffBid(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        if (cutOffMode != CutOffMode.BID) {
            return false;
        }
        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(5)) < 20) {
            return false;
        }
        Rate rate = snapshot.getRate();
//        if (rateAnalyzer.getAskThreshold() <= rate.getAsk()) {
//            return true;
//        }
//        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(10)) > 30 && rateAnalyzer.maxWithin(Duration.ofMinutes(1)) <= rate.getAsk()) {
//            return true;
//        }
        if (rateAnalyzer.maxWithin(Duration.ofMinutes(1)) <= rate.getAsk()) {
            return true;
        }
        return false;
    }

}
