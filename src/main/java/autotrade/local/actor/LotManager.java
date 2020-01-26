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
    private int limitPositive;
    private int limitNegative;
    private Mode mode;

    private enum Mode {
        POSITIVE,
        NEGATIVE
    }

    public LotManager() {
        initialPositive = AutoTradeProperties.getInt("autotrade.lot.initial.positive");
        initialNegative = AutoTradeProperties.getInt("autotrade.lot.initial.negative");
        nextMagnification = AutoTradeProperties.getInt("autotrade.lot.nextMagnification");
        limitPositive = AutoTradeProperties.getInt("autotrade.lot.limit.positive");
        limitNegative = AutoTradeProperties.getInt("autotrade.lot.limit.negative");
        modePositive();
    }

    public int nextAskLot(Snapshot snapshot) {
        if (snapshot.getBidLot() == 0) {
            return getInitial();
        }
        int limit = getlimit();
        int nextLot = snapshot.getBidLot() * nextMagnification - snapshot.getAskLot();
        return snapshot.getBidLot() >= limit ? snapshot.getBidLot() - snapshot.getAskLot() : nextLot;
    }
    public int nextBidLot(Snapshot snapshot) {
        if (snapshot.getAskLot() == 0) {
            return getInitial();
        }
        int limit = getlimit();
        int nextLot = snapshot.getAskLot() * nextMagnification - snapshot.getBidLot();
        return snapshot.getAskLot() >= limit ? snapshot.getAskLot() - snapshot.getBidLot() : nextLot;
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
    public boolean isPositive() {
        if (Mode.POSITIVE == mode) {
            return true;
        }
        return false;
    }
    public boolean isNegative() {
        return !isPositive();
    }
    public boolean isLimit(Snapshot snapshot) {
        if (snapshot.getAskLot() >= getlimit()
                || snapshot.getBidLot() >= getlimit()) {
            return true;
        }
        return false;
    }

    private int getlimit() {
        switch (mode) {
        case POSITIVE:
            return limitPositive;
        case NEGATIVE:
            return limitNegative;
        default:
            return limitPositive;
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
