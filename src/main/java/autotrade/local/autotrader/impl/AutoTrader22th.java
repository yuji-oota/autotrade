package autotrade.local.autotrader.impl;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import autotrade.local.actor.RangeManager;
import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AbstractAutoTrader;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Pair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 21thから派生
 *
 */
@Component("autoTrader22th")
@Slf4j
public class AutoTrader22th extends AbstractAutoTrader {

    private boolean doAsk;
    private boolean doBid;
    private int stopLossRate;

    @Autowired
    private RecoveryManager recoveryManager;

    @Autowired
    private RangeManager rangeManager;

    @Autowired
    private ToIntFunction<Snapshot> toMinimumProfit;

    @Autowired
    private ToIntFunction<Snapshot> toInitialLot;

    @Autowired
    private ToIntFunction<Snapshot> toNextLot;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader22th.stopLoss.duration.seconds}')}")
    private Duration stopLossDuration;

    public AutoTrader22th() {
        super();
        log.info("autoTrader22th started.");
    }

    @Override
    protected void saveLocal() {
        super.saveLocal();
        AutoTradeUtils.localSave(Paths.get("localSave", "recoveryManager"), recoveryManager);
        AutoTradeUtils.localSave(Paths.get("localSave", "stopLossRate"), stopLossRate);
        AutoTradeUtils.localSave(Paths.get("localSave", "rangeManager"), rangeManager);
    }

    @Override
    protected void loadLocal() {
        super.loadLocal();
        recoveryManager = AutoTradeUtils.localLoad(Paths.get("localSave", "recoveryManager"));
        log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
        stopLossRate = AutoTradeUtils.localLoad(Paths.get("localSave", "stopLossRate"));
        rangeManager = AutoTradeUtils.localLoad(Paths.get("localSave", "rangeManager"));
    }

    @Override
    protected Pair selectPair() {

        if (recoveryManager.isOpen()) {
            return recoveryManager.getHandlePair();
        }

        changeDisplay(DisplayMode.RATELIST);
        // 推奨通貨ペア選択
        LocalTime now = LocalTime.now();
        return pairManager.getPairs().stream()
                .filter(pair -> pair.isHandleable(now))
                .map(pair -> {
                    return new SimpleEntry<Pair, Integer>(
                            pair,
                            pairAnalyzerMap.get(pair.getName()).rangeWithin(stopLossDuration) - pair.getMinSpread());
                })
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(SimpleEntry::getKey)
                .orElseGet(pairManager::getDefault);
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.hasNoPosition();
    }

    @Override
    protected void preTrade(Snapshot snapshot) {
        if (recoveryManager.isOpen()
                && isSleep(snapshot)) {
            rangeManager.reset();
            saveLocal();
        }

        super.preTrade(snapshot);

        if (indicatorManager.isPrevImportant()
                && indicatorManager.isPrevIndicatorWithin(Duration.ofSeconds(15))) {
            rangeManager.reset();
        }
    }

    @Override
    protected void preFix(Snapshot snapshot) {
        if (recoveryManager.isOpen()) {
            rangeManager.save(snapshot);
        }
        if (isShiftStopLossRate(snapshot)) {
            if (snapshot.hasAskOnly()) {
                int minRate = rateAnalyzer.minWithin(stopLossDuration);
                if (stopLossRate < minRate) {
                    stopLossRate = minRate;
                    printRecoveryProgress(snapshot);
                }
            }
            if (snapshot.hasBidOnly()) {
                int maxRate = rateAnalyzer.maxWithin(stopLossDuration);
                if (stopLossRate > maxRate) {
                    stopLossRate = maxRate;
                    printRecoveryProgress(snapshot);
                }
            }
            rangeManager.reset();
        }
    }

    @Override
    protected boolean fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        if (indicatorManager.isNextImportant()
                && indicatorManager.isNextIndicatorWithin(Duration.ofSeconds(90))) {
            log.info("stop loss in preparation for important indicators.");
            stopLossProcess(snapshot);
            return true;
        }

        if (rangeManager.isWithinRange(snapshot)
                && snapshot.hasProfit()) {
            if (rateAnalyzer.isAskUp()
                    && snapshot.hasBid()
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                stopLossProcess(snapshot);
                return true;
            }
            if (rateAnalyzer.isBidDown()
                    && snapshot.hasAsk()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                stopLossProcess(snapshot);
                return true;
            }
        }

        if (recoveryManager.isRecoveredWithProfit(snapshot)) {
            if (rateAnalyzer.isBidDown()
                    && snapshot.isBidLtAsk()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                fixAll(snapshot);
                return true;
            }
            if (rateAnalyzer.isAskUp()
                    && snapshot.isBidGtAsk()
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                fixAll(snapshot);
                return true;
            }
        } else {
            if (isAboveStopLossRate(snapshot.getRate())) {
                if (rateAnalyzer.isAskUp()
                        && snapshot.hasBid()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    stopLossProcess(snapshot);
                    return true;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && snapshot.hasAsk()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    stopLossProcess(snapshot);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        if (!super.isOrderable(snapshot)) {
            return false;
        }
        if (recoveryManager.isOpen()
                && !recoveryManager.getHandlePair().equals(snapshot.getPair())) {
            return false;
        }
        return true;
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NO_POSITION:
            // ポジションがない場合

            if (rateAnalyzer.isUpwardWithin(stopLossDuration)) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    stopLossRate = rateAnalyzer.minWithin(stopLossDuration);
                    if (recoveryManager.isClose()) {
                        recoveryManager.open(snapshot);
                        rangeManager.reset();
                        orderAsk(toInitialLot.applyAsInt(snapshot), snapshot);
                    } else {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        rangeManager.apply();
                        orderAsk(recoveryManager.getCounterTradingStartLot(), snapshot);
                    }
                    printRecoveryProgress(snapshot);
                    doAsk = false;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    stopLossRate = rateAnalyzer.maxWithin(stopLossDuration);
                    if (recoveryManager.isClose()) {
                        recoveryManager.open(snapshot);
                        rangeManager.reset();
                        orderBid(toInitialLot.applyAsInt(snapshot), snapshot);
                    } else {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        rangeManager.apply();
                        orderBid(recoveryManager.getCounterTradingStartLot(), snapshot);
                    }
                    printRecoveryProgress(snapshot);
                    doBid = false;
                }
            }

            break;
        case BID_LT_ASK:
            // 買いポジションが多い場合
        case BID_GT_ASK:
            // 売りポジションが多い場合
        case BID_EQ_ASK:
            // ポジションが同数の場合

            if (isAboveStopLossRate(snapshot.getRate())) {
                if (rateAnalyzer.isAskUp()) {
                    if (doAsk
                            && snapshot.isAskLtLimit()
                            && rateAnalyzer.isReachedAskThreshold(rate)) {
                        orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
                        doAsk = false;
                        return;
                    }
                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        orderBid(toNextLot.applyAsInt(snapshot), snapshot);
                        doBid = false;
                        return;
                    }
                }
            }

            break;
        default:
        }

    }

    @Override
    protected void postOrder(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        if (rateAnalyzer.isAskUp()
                && rateAnalyzer.isReachedAskThreshold(rate)) {
            doBid = true;
        }
        if (rateAnalyzer.isBidDown()
                && rateAnalyzer.isReachedBidThreshold(rate)) {
            doAsk = true;
        }
    }

    private boolean isAboveStopLossRate(Rate rate) {
        return stopLossRate < rate.getMiddle();
    }

    private boolean isShiftStopLossRate(Snapshot snapshot) {
        if (recoveryManager.isOpen()) {
            if (isStopLossRateNegativeZone(snapshot)) {
                return true;
            }
            if (recoveryManager.isReachedRecoveryProgress(snapshot)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStopLossRateNegativeZone(Snapshot snapshot) {
        if (snapshot.isBidLtAsk()) {
            if (stopLossRate < recoveryManager.getCounterTradingSnapshot().getRate().getAsk()) {
                return true;
            }
        }
        if (snapshot.isBidGtAsk()) {
            if (stopLossRate > recoveryManager.getCounterTradingSnapshot().getRate().getBid()) {
                return true;
            }
        }
        return false;
    }

    private void stopLossProcess(Snapshot snapshot) {
        if (recoveryManager.isRecoveredWithProfit(snapshot, toMinimumProfit)) {
            fixAll(snapshot);
            return;
        }
        if (snapshot.hasAsk()) {
            fixAsk(snapshot);
        }
        if (snapshot.hasBid()) {
            fixBid(snapshot);
        }
        recoveryManager.stopLossProcess(snapshot);
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        recoveryManager.printSummary(snapshot);
        recoveryManager.close();
    }

    private void printRecoveryProgress(Snapshot snapshot) {
        log.info("recovery progress:{} profit:{} stopLossRate:{}",
                recoveryManager.getRecoveryProgress(snapshot),
                recoveryManager.getProfit(snapshot),
                stopLossRate);
    }

}
