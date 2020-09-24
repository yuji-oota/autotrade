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
    private Snapshot snapshotWhenStart;
    private Snapshot shapshotWhenCutOffAsk;
    private Snapshot shapshotWhenCutOffBid;

    public void start(Snapshot snapshot) {
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
    public void done() {
        isOpen = false;
        cutOffDone();
    }
    public void cutOffDone() {
        isCutOffAsk = false;
        isCutOffBid = false;
    }
    public boolean isClose() {
        return !isOpen;
    }

    public boolean isRecovered(Snapshot snapshot) {
        return snapshotWhenStart.getMargin() <= snapshot.getMargin() + snapshot.getPositionProfit();
    }
    public boolean isSuccessCutOffAsk(Snapshot snapshot) {
        return shapshotWhenCutOffAsk.getRate().getBid() >= snapshot.getRate().getAsk();
    }
    public boolean isSuccessCutOffBid(Snapshot snapshot) {
        return shapshotWhenCutOffBid.getRate().getAsk() <= snapshot.getRate().getBid();
    }
}
