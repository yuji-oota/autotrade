package autotrade.local.actor;

import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LotManager {

    @Getter
    private int initialLot;
    private int nextMagnification;
    private int sameLimitPositive;
    private int sameLimitNegative;
    private Mode mode;

    private enum Mode {
        POSITIVE,
        NEGATIVE
    }

    public LotManager() {
        initialLot = Integer.parseInt(AutoTradeProperties.get("autotrade.lot.initial"));
        nextMagnification = Integer.parseInt(AutoTradeProperties.get("autotrade.lot.nextMagnification"));
        sameLimitPositive = Integer.parseInt(AutoTradeProperties.get("autotrade.lot.sameLimit.positive"));
        sameLimitNegative = Integer.parseInt(AutoTradeProperties.get("autotrade.lot.sameLimit.negative"));
        modePositive();
    }

    public int nextAskLot(Snapshot snapshot) {
        int sameLimit = getSameLimit();
        int nextLot = snapshot.getBidLot() * nextMagnification - snapshot.getAskLot();
        return snapshot.getBidLot() >= sameLimit ? snapshot.getBidLot() - snapshot.getAskLot() : nextLot;
    }
    public int nextBidLot(Snapshot snapshot) {
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

}
