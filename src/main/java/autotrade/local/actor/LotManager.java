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

    public int nextLot(Snapshot snapshot) {
        if (snapshot.isPositionNone()) {
            return getInitial();
        }
        int more = snapshot.getAskLot();
        int less = snapshot.getBidLot();
        if (snapshot.isPositionBidSide()) {
            more = snapshot.getBidLot();
            less = snapshot.getAskLot();
        }
        return more >= getlimit() ? more - less : (more * nextMagnification) - less;
    }
    public void modePositive() {
        if (isPositive()) {
            return;
        }
        mode = Mode.POSITIVE;
        log.info("mode is changed to positive.");
    }
    public void modeNegative() {
        if (isNegative()) {
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
        if (isPositive()) {
            return limitPositive;
        }
        return limitNegative;
    }
    public int getInitial() {
        if (isPositive()) {
            return initialPositive;
        }
        return initialNegative;
    }

}
