package autotrade.local.actor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.function.ToIntFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import autotrade.local.material.Pair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Getter
public class RecoveryManager implements Serializable {

    private boolean isOpen;
    private Snapshot openSnapshot;
    private int stopLossCount;
    private int openCount;
    private int counterTradingStartLot;

    @Setter
    private Snapshot counterTradingSnapshot;

    @Autowired
    private ToIntFunction<Snapshot> toProfit;

    @Autowired
    private ToIntFunction<Snapshot> toInitialLot;

    @Autowired
    private ToIntFunction<Snapshot> toTargetProgress;

    @Value("${autotrade.config.toTargetProgress.maxRatio}")
    private int maxRatio;

    @Value("${autotrade.config.toTargetProgress.ratioRange}")
    private BigDecimal ratioRange;

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
        log.info("RecoveryManager closed.");
    }

    public boolean isClose() {
        return !isOpen;
    }

    public boolean isRecovered(Snapshot snapshot) {
        return isRecovered(openSnapshot.getMargin(), snapshot.getEffectiveMargin());
    }

    private boolean isRecovered(int startMargin, int effectiveMargin) {
        return startMargin <= effectiveMargin;
    }

    public boolean isRecoveredWithProfit(Snapshot snapshot) {
        return isRecoveredWithProfit(snapshot, toProfit);
    }

    public boolean isRecoveredWithProfit(Snapshot snapshot, ToIntFunction<Snapshot> toProfit) {
        return isRecovered(openSnapshot.getMargin() + toProfit.applyAsInt(openSnapshot),
                snapshot.getEffectiveMargin());
    }

    public boolean isSuccessCounterTradingAsk(Rate rate) {
        return counterTradingSnapshot.getRate().getAsk() <= rate.getBid();
    }

    public boolean isSuccessCounterTradingBid(Rate rate) {
        return counterTradingSnapshot.getRate().getBid() >= rate.getAsk();
    }

    public int getRecoveryProgress(Snapshot snapshot) {
        int startProfit = getStartProfit() - toProfit.applyAsInt(openSnapshot);
        int profit = getProfit(snapshot) - toProfit.applyAsInt(openSnapshot);
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

    public int getProfit(Snapshot snapshot) {
        return snapshot.getEffectiveMargin() - openSnapshot.getMargin();
    }

    public void printSummary(Snapshot snapshot) {
        log.info("recovery summary. {} lot:{} start profit:{} end profit:{} total profit:{} stopLossCount:{}",
                snapshot.getPair().getName(), snapshot.getMoreLot(),
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
        counterTradingStartLot = Math.max(toInitialLot.applyAsInt(openSnapshot), counterTradingStartLot);
        stopLossCount++;
    }

    public Pair getHandlePair() {
        return openSnapshot.getPair();
    }

    public boolean isReachedRecoveryProgress(Snapshot snapshot) {
        return getRecoveryProgress(snapshot) >= toTargetProgress(snapshot);
    }

    private int toTargetProgress(Snapshot snapshot) {
        BigDecimal limitSubInitial = new BigDecimal(
                openSnapshot.getLimitLot() - toInitialLot.applyAsInt(openSnapshot));
        BigDecimal currentSubInitial = new BigDecimal(snapshot.getMoreLot() - toInitialLot.applyAsInt(openSnapshot));
        BigDecimal progressUnit = limitSubInitial.divide(ratioRange, 1, RoundingMode.HALF_UP);
        return maxRatio - currentSubInitial.divide(progressUnit, 0, RoundingMode.HALF_UP).intValue();
    }

}
