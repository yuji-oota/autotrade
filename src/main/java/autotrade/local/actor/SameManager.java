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
    private Mode mode;

    enum Mode {
        ACTIVE,
        INACTIVE
    }

    @Setter
    @Getter
    private Snapshot shapshotWhenCutOff;

    private SameManager(Snapshot snapshot) {
        this.snapshotWhenSamed = snapshot;
        this.mode = Mode.ACTIVE;
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
        if (snapshot.getTotalProfit() >= snapshotWhenSamed.getTodaysProfit() + (snapshotWhenSamed.getPositionProfit() / 2)) {
            return true;
        }
        return false;
    }

    public static void close() {
        instance = null;
    }

    public boolean isCutOffAsk(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        if (mode != Mode.ACTIVE) {
            return false;
        }
        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(5)) < 30) {
            return false;
        }
        Rate rate = snapshot.getRate();
        if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
            return true;
        }
        return false;
    }

    public boolean isCutOffBid(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        if (mode != Mode.ACTIVE) {
            return false;
        }
        if (rateAnalyzer.rangeWithin(Duration.ofMinutes(5)) < 30) {
            return false;
        }
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
            log.info("margin is recovered just a little.");
            return true;
        }
        if (rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(2))) {
            return true;
        }
        if (shapshotWhenCutOff.getRate().getBid() - rate.getAsk() <= -10) {
            return true;
        }
        return false;
    }

    public boolean isReSameBid(Snapshot snapshot, RateAnalyzer rateAnalyzer) {
        Rate rate = snapshot.getRate();
        if (rate.getBid() - shapshotWhenCutOff.getRate().getAsk() > 0
                && rate.getBid() <= rateAnalyzer.minWithin(Duration.ofSeconds(15))) {
            log.info("margin is recovered just a little.");
            return true;
        }
        if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(2))) {
            return true;
        }
        if (rate.getBid() - shapshotWhenCutOff.getRate().getAsk() <= -10) {
            return true;
        }
        return false;
    }

    public void modeActive() {
        if (Mode.ACTIVE == mode) {
            return;
        }
        mode = Mode.ACTIVE;
        log.info("mode is changed to active.");
    }
    public void modeInactive() {
        if (Mode.INACTIVE == mode) {
            return;
        }
        mode = Mode.INACTIVE;
        log.info("mode is changed to inactive.");
    }
}
