package autotrade.local.actor;

import java.io.Serializable;
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
public class RecoveryManager implements Serializable {

    private boolean isOpen;
    private boolean isReachedRecover;
    private Snapshot snapshotWhenStart;
    private ToIntFunction<Snapshot> profitCalcurator;

    @Setter
    private Snapshot counterTradingSnapshot;
    @Setter
    private Snapshot snapshotWhenStopLoss;

    @SuppressWarnings("unchecked")
    public RecoveryManager() {
        profitCalcurator = (ToIntFunction<Snapshot> & Serializable) s -> s.getMargin() / 10000;
    }
    public RecoveryManager(ToIntFunction<Snapshot> profitCalcurator) {
        this.profitCalcurator = profitCalcurator;
    }

    public void open(Snapshot snapshot) {
        snapshotWhenStart = snapshot;
        counterTradingSnapshot = snapshot;
        snapshotWhenStopLoss = snapshot;
        log.info("RecoveryManager opened {}.", snapshot);
        isOpen = true;
    }
    public void close() {
        isOpen = false;
        resetReachedRecover();
        log.info("RecoveryManager closed.");
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
