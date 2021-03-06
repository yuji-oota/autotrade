package autotrade.local.actor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.ToIntFunction;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.Getter;
import lombok.Setter;
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
    private ToIntFunction<Snapshot> profitCalcurator;

    @Setter
    private Snapshot counterTradingSnapshot;

    public RecoveryManager() {
        profitCalcurator = s -> s.getMargin() / 10000;
    }
    public RecoveryManager(ToIntFunction<Snapshot> profitCalcurator) {
        this.profitCalcurator = profitCalcurator;
    }

    public void open(Snapshot snapshot) {
        if (!isOpen) {
            snapshotWhenStart = snapshot;
            counterTradingSnapshot = snapshot;
            log.info("RecoveryManager opened {}.", snapshot);
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
//        log.info("recovery done.");
    }
    public void cutOffDone() {
        isCutOffAsk = false;
        isCutOffBid = false;
//        log.info("cut off done.");
    }
    public void resetReachedRecover() {
        isReachedRecover = false;
    }
    public boolean isClose() {
        return !isOpen;
    }
    public boolean isRecovered(Snapshot snapshot) {
        return isRecovered(snapshotWhenStart.getMargin(), snapshot.getEffectiveMargin());
    }
    public boolean isRecoveredWithProfit(Snapshot snapshot) {
        return isRecovered(snapshotWhenStart.getMargin() + profitCalcurator.applyAsInt(snapshot), snapshot.getEffectiveMargin());
    }
    private boolean isRecovered(int startMargin, int effectiveMargin) {
        boolean isRecovered = startMargin <= effectiveMargin;
        if (!isReachedRecover && isRecovered) {
            log.info("reached recovery.");
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
    public boolean isSuccessCounterTradingAsk(Rate rate) {
        return counterTradingSnapshot.getRate().getAsk() <= rate.getBid();
    }
    public boolean isSuccessCounterTradingBid(Rate rate) {
        return counterTradingSnapshot.getRate().getBid() >= rate.getAsk();
    }
    public int getRecoveryProgress(Snapshot snapshot) {
        int lossCounterTradingStart = getLossCounterTradingStart();
        int loss = getLoss(snapshot);
        if (lossCounterTradingStart == 0) {
            return 0;
        }
        return BigDecimal.ONE
                .subtract(BigDecimal.valueOf(loss).divide(BigDecimal.valueOf(lossCounterTradingStart), new MathContext(2, RoundingMode.HALF_UP)))
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }
    private int getLossCounterTradingStart() {
        return counterTradingSnapshot.getMargin() + counterTradingSnapshot.getPositionProfit() - snapshotWhenStart.getMargin();
    }
    private int getLoss(Snapshot snapshot) {
        return snapshot.getEffectiveMargin() - snapshotWhenStart.getMargin();
    }
    public void printSummary(Snapshot snapshot) {
        log.info("recovery progress is {}%. start:{} end:{}",
                getRecoveryProgress(snapshot), getLossCounterTradingStart(), getLoss(snapshot));
    }
    public boolean isBeforeCounterTrading() {
        return snapshotWhenStart.equals(counterTradingSnapshot);
    }
    public boolean isAfterCounterTrading() {
        return !isBeforeCounterTrading();
    }
}
