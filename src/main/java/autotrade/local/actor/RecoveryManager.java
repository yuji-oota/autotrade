package autotrade.local.actor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.ToIntFunction;

import autotrade.local.material.CurrencyPair;
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
    private int stopLossCount;

    @Setter
    private Snapshot counterTradingSnapshot;

    @Setter
    private int counterTradingStartLot;

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
        log.info("RecoveryManager opened.");
        isOpen = true;
        stopLossCount = 0;
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
        return isRecovered(snapshotWhenStart.getMargin() + profitCalcurator.applyAsInt(snapshot),
                snapshot.getEffectiveMargin());
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
        int startProfit = getStartProfit();
        int profit = getProfit(snapshot);
        if (startProfit == 0) {
            return 0;
        }
        return BigDecimal.ONE
                .subtract(BigDecimal.valueOf(profit).divide(BigDecimal.valueOf(startProfit),
                        new MathContext(2, RoundingMode.HALF_UP)))
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }

    private int getStartProfit() {
        return counterTradingSnapshot.getMargin()
                + counterTradingSnapshot.getPositionProfit()
                - snapshotWhenStart.getMargin();
    }

    private int getProfit(Snapshot snapshot) {
        return snapshot.getEffectiveMargin() - snapshotWhenStart.getMargin();
    }

    public void printSummary(Snapshot snapshot) {
        log.info("recovery summary. {} lot:{} start profit:{} end profit:{} total profit:{} stopLossCount:{}",
                snapshot.getPair(), snapshot.getMoreLot(),
                getStartProfit(), getProfit(snapshot),
                snapshot.getTotalProfit(), stopLossCount);
    }

    public boolean isBeforeCounterTrading() {
        return snapshotWhenStart.equals(counterTradingSnapshot);
    }

    public boolean isAfterCounterTrading() {
        return !isBeforeCounterTrading();
    }

    public void stopLossProcess(Snapshot snapshot) {
        printSummary(snapshot);
        counterTradingStartLot = snapshot.getMoreLot();
        if (snapshot.hasProfit()) {
            int percentage = 100 - getRecoveryProgress(snapshot);
            counterTradingStartLot = snapshot.getMoreLot() * percentage / 100;
        }
        stopLossCount++;
        resetReachedRecover();
    }

    public CurrencyPair getHandlePair() {
        return snapshotWhenStart.getPair();
    }
}
