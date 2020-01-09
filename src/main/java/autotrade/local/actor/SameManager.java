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
    private Snapshot snapshotWhenSamed;

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

    private SameManager(Snapshot snapshot) {
        this.snapshotWhenSamed = snapshot;
        this.cutOffMode = CutOffMode.NONE;
    }

    public static void setSnapshot(Snapshot snapshot) {
        if (Objects.isNull(instance)) {
            instance = new SameManager(snapshot);
            log.info("same position started. snapshot {}", snapshot);
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

    public boolean isRecovered(Snapshot snapshot) {
        if (snapshot.getTotalProfit() >= snapshotWhenSamed.getTodaysProfit()) {
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
//        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(10)) < 40) {
//            return false;
//        }
        Rate rate = snapshot.getRate();
        if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
            return true;
        }
        return false;
    }

    public boolean isCutOffBid(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        if (cutOffMode != CutOffMode.BID) {
            return false;
        }
//        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(10)) < 40) {
//            return false;
//        }
        Rate rate = snapshot.getRate();
        if (rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
            return true;
        }
        return false;
    }

    public boolean isReSameAsk(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        Rate rate = snapshot.getRate();
        if (shapshotWhenCutOff.getRate().getBid() - rate.getAsk() > 0
                && rateAnalyzer.maxWithin(Duration.ofSeconds(15)) <= rate.getAsk()) {
            log.info("margin is recovered just a little. snapshot {}", snapshot);
            return true;
        }
        if (rateAnalyzer.isReachedAskThreshold(rate)) {
            return true;
        }
        return false;
    }

    public boolean isReSameBid(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        Rate rate = snapshot.getRate();
        if (rate.getBid() - shapshotWhenCutOff.getRate().getAsk() > 0
                && rate.getBid() <= rateAnalyzer.minWithin(Duration.ofSeconds(15))) {
            log.info("margin is recovered just a little. snapshot {}", snapshot);
            return true;
        }
        if (rateAnalyzer.isReachedBidThreshold(rate)) {
            return true;
        }
        return false;
    }
}
