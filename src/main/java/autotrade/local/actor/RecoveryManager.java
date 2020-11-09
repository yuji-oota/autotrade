package autotrade.local.actor;

import autotrade.local.material.Snapshot;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class RecoveryManager {

    private boolean isOpen;
    private boolean isCutOffAsk;
    private boolean isCutOffBid;
    private boolean isReachedRecover;
    private Snapshot snapshotWhenStart;
    private Snapshot shapshotWhenCutOffAsk;
    private Snapshot shapshotWhenCutOffBid;

    public void open(Snapshot snapshot) {
        if (!isOpen) {
            snapshotWhenStart = snapshot;
        }
        isOpen = true;
    }
    public void cutOffAsk(Snapshot snapshot) {
        shapshotWhenCutOffAsk = snapshot;
        isCutOffAsk = true;
        log.info("cut off ask.");
    }
    public void cutOffBid(Snapshot snapshot) {
        shapshotWhenCutOffBid = snapshot;
        isCutOffBid = true;
        log.info("cut off bid.");
    }
    public void close() {
        isOpen = false;
        resetReachedRecover();
        cutOffDone();
        log.info("recovery done.");
    }
    public void cutOffDone() {
        isCutOffAsk = false;
        isCutOffBid = false;
        log.info("cut off done.");
    }
    public void resetReachedRecover() {
        isReachedRecover = false;
    }
    public boolean isClose() {
        return !isOpen;
    }

    public boolean isRecovered(Snapshot snapshot) {
        boolean isRecovered = snapshotWhenStart.getMargin() <= snapshot.getMargin() + snapshot.getPositionProfit();
        if (!isReachedRecover) {
            isReachedRecover = isRecovered;
        }
        return isRecovered;
    }
    public boolean isSuccessCutOffAsk(Snapshot snapshot) {
        return shapshotWhenCutOffAsk.getRate().getBid() >= snapshot.getRate().getAsk();
    }
    public boolean isSuccessCutOffBid(Snapshot snapshot) {
        return shapshotWhenCutOffBid.getRate().getAsk() <= snapshot.getRate().getBid();
    }
}
