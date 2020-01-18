package autotrade.local.actor;

import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LotManager {

    @Getter
    private int initialPositive;
    private int initialNegative;
    private int nextMagnification;
    private int sameLimitPositive;
    private int sameLimitNegative;
    private Mode mode;

    private enum Mode {
        POSITIVE,
        NEGATIVE
    }

    public LotManager() {
        initialPositive = AutoTradeProperties.getInt("autotrade.lot.initial.positive");
        initialNegative = AutoTradeProperties.getInt("autotrade.lot.initial.negative");
        nextMagnification = AutoTradeProperties.getInt("autotrade.lot.nextMagnification");
        sameLimitPositive = AutoTradeProperties.getInt("autotrade.lot.sameLimit.positive");
        sameLimitNegative = AutoTradeProperties.getInt("autotrade.lot.sameLimit.negative");
        modePositive();
    }

    public int nextAskLot(Snapshot snapshot) {
        if (snapshot.getBidLot() == 0) {
            return getInitial();
        }
        int sameLimit = getSameLimit();
        int nextLot = snapshot.getBidLot() * nextMagnification - snapshot.getAskLot();
        return snapshot.getBidLot() >= sameLimit ? snapshot.getBidLot() - snapshot.getAskLot() : nextLot;
    }
    public int nextBidLot(Snapshot snapshot) {
        if (snapshot.getAskLot() == 0) {
            return getInitial();
        }
        int sameLimit = getSameLimit();
        int nextLot = snapshot.getAskLot() * nextMagnification - snapshot.getBidLot();
        return snapshot.getAskLot() >= sameLimit ? snapshot.getAskLot() - snapshot.getBidLot() : nextLot;
    }
    public void modePositive() {
        if (Mode.POSITIVE == mode) {
            return;
        }
        mode = Mode.POSITIVE;
        log.info("mode is changed to positive.");
    }
    public void modeNegative() {
        if (Mode.NEGATIVE == mode) {
            return;
        }
        mode = Mode.NEGATIVE;
        log.info("mode is changed to negative.");
    }

    private int getSameLimit() {
        switch (mode) {
        case POSITIVE:
            return sameLimitPositive;
        case NEGATIVE:
            return sameLimitNegative;
        default:
            return sameLimitPositive;
        }
    }
    private int getInitial() {
        switch (mode) {
        case POSITIVE:
            return initialPositive;
        case NEGATIVE:
            return initialNegative;
        default:
            return initialPositive;
        }
    }

}
