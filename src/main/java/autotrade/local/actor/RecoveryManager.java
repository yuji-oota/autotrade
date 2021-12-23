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
    private Snapshot openSnapshot;
    private ToIntFunction<Snapshot> profitCalcurator;
    private int stopLossCount;
    private int openCount;

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
        openSnapshot = snapshot;
        counterTradingSnapshot = snapshot;
        log.info("RecoveryManager opened.");
        isOpen = true;
        stopLossCount = 0;
        openCount++;
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
        return isRecovered(openSnapshot.getMargin(), snapshot.getEffectiveMargin());
    }

    public boolean isRecoveredWithProfit(Snapshot snapshot) {
        return isRecoveredWithProfit(snapshot, profitCalcurator);
    }

    public boolean isRecoveredWithProfit(Snapshot snapshot, ToIntFunction<Snapshot> toProfit) {
        return isRecovered(openSnapshot.getMargin() + toProfit.applyAsInt(openSnapshot),
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
        int startProfit = getStartProfit() - profitCalcurator.applyAsInt(openSnapshot);
        int profit = getProfit(snapshot) - profitCalcurator.applyAsInt(openSnapshot);
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
                - openSnapshot.getMargin();
    }

    private int getProfit(Snapshot snapshot) {
        return snapshot.getEffectiveMargin() - openSnapshot.getMargin();
    }

    public void printSummary(Snapshot snapshot) {
        log.info("recovery summary. {} lot:{} start profit:{} end profit:{} total profit:{} stopLossCount:{}",
                snapshot.getPair(), snapshot.getMoreLot(),
                getStartProfit(), getProfit(snapshot),
                snapshot.getTotalProfit(), stopLossCount);
    }

    public boolean isBeforeCounterTrading() {
        return stopLossCount == 0;
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
        return openSnapshot.getPair();
    }
}
