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
 * 25thから派生
 * レンジ内は細かく利益確定
 *
 */
@Component("autoTrader29th")
@Slf4j
public class AutoTrader29th extends AbstractAutoTrader {

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
    private ToIntFunction<Snapshot> toNextLot;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader29th.stopLoss.duration.seconds}')}")
    private Duration stopLossDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader29th.order.duration.seconds}')}")
    private Duration orderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader29th.followUpOrder.duration.seconds}')}")
    private Duration followUpOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader29th.doOrder.duration.seconds}')}")
    private Duration doOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader29th.fix.duration.seconds}')}")
    private Duration fixDuration;

    public AutoTrader29th() {
        super();
        log.info("autoTrader29th started.");
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
                .filter(pair -> buildRateFromList(pair).isSpreadNarrow())
                .map(pair -> {
                    return new SimpleEntry<Pair, Integer>(
                            pair,
                            pairAnalyzerMap.get(pair.getName()).rangeWithin(orderDuration) - pair.getMinSpread());
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

    protected boolean isTradable(Snapshot snapshot) {
        if (!super.isTradable(snapshot)) {
            return false;
        }
        if (recoveryManager.isOpen()
                && !recoveryManager.getHandlePair().equals(snapshot.getPair())) {
            return false;
        }
        return true;
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
                && rateAnalyzer.getRates().isEmpty()) {
            rangeManager.reset();
        }
    }

    @Override
    protected void preFix(Snapshot snapshot) {
        if (recoveryManager.isOpen()) {
            if (!snapshot.isSpreadWiden()) {
                rangeManager.save(snapshot);

                if (rangeManager.isFirstSave()) {
                    int min = rateAnalyzer.minWithin(stopLossDuration);
                    int max = rateAnalyzer.maxWithin(stopLossDuration);
                    if (Integer.MIN_VALUE < min && max < Integer.MAX_VALUE) {
                        rangeManager.adjustTermination(rateAnalyzer.minWithin(stopLossDuration),
                                rateAnalyzer.maxWithin(stopLossDuration));
                    }
                }
            }
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

        if (recoveryManager.isRecoveredWithProfit(snapshot, toMinimumProfit)) {
            if (rateAnalyzer.isBidDown()
                    && snapshot.isBidLtAsk()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, fixDuration)) {
                fixAll(snapshot);
                return true;
            }
            if (rateAnalyzer.isAskUp()
                    && snapshot.isBidGtAsk()
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, fixDuration)) {
                fixAll(snapshot);
                return true;
            }
        } else {
//            if (isAboveStopLossRate(snapshot.getRate())) {
//                if (rateAnalyzer.isAskUp()
//                        && snapshot.hasBid()
//                        && rateAnalyzer.isReachedAskThreshold(rate)) {
//                    stopLossProcess(snapshot);
//                    return true;
//                }
//            }
//            if (isBelowStopLossRate(snapshot.getRate())) {
//                if (rateAnalyzer.isBidDown()
//                        && snapshot.hasAsk()
//                        && rateAnalyzer.isReachedBidThreshold(rate)) {
//                    stopLossProcess(snapshot);
//                    return true;
//                }
//            }
        }
        return false;
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        if (!super.isOrderable(snapshot)) {
            return false;
        }
        if (rateAnalyzer.getRates().size() == rateAnalyzer.ratesWithin(orderDuration).size()) {
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

            if (recoveryManager.isClose()) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, orderDuration)) {
                    stopLossRate = rateAnalyzer.minWithin(stopLossDuration);
                    recoveryManager.open(snapshot);
                    rangeManager.reset();
                    orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
                    printRecoveryProgress(snapshot);
                    doAsk = false;
                }
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDuration)) {
                    stopLossRate = rateAnalyzer.maxWithin(stopLossDuration);
                    recoveryManager.open(snapshot);
                    rangeManager.reset();
                    orderBid(toNextLot.applyAsInt(snapshot), snapshot);
                    printRecoveryProgress(snapshot);
                    doBid = false;
                }
                return;
            }

            if (recoveryManager.isOpen()) {

                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, orderDuration)) {
                    stopLossRate = rangeManager.getLowerLimitSave().getBid();
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    orderAsk(recoveryManager.getCounterTradingStartLot(), snapshot);
                    printRecoveryProgress(snapshot);
                    doAsk = false;
                    return;
                }
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDuration)) {
                    stopLossRate = rangeManager.getUpperLimitSave().getAsk();
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    orderBid(recoveryManager.getCounterTradingStartLot(), snapshot);
                    printRecoveryProgress(snapshot);
                    doBid = false;
                    return;
                }

                return;
            }

            break;
        case BID_LT_ASK:
            // 買いポジションが多い場合
        case BID_GT_ASK:
            // 売りポジションが多い場合
        case BID_EQ_ASK:
            // ポジションが同数の場合

//            if (isAboveStopLossRate(snapshot.getRate())) {
//                if (rateAnalyzer.isAskUp()) {
//                    if (doAsk
//                            && snapshot.isAskLtLimit()
//                            && snapshot.hasAskOnly()
//                            && rateAnalyzer.isReachedAskThresholdWithin(rate, followUpOrderDuration)) {
//                        orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
//                        doAsk = false;
//                        return;
//                    }
//                }
//            }
//            if (isBelowStopLossRate(snapshot.getRate())) {
//                if (rateAnalyzer.isBidDown()) {
//                    if (doBid
//                            && snapshot.isBidLtLimit()
//                            && snapshot.hasBidOnly()
//                            && rateAnalyzer.isReachedBidThresholdWithin(rate, followUpOrderDuration)) {
//                        orderBid(toNextLot.applyAsInt(snapshot), snapshot);
//                        doBid = false;
//                        return;
//                    }
//                }
//            }
            if (rateAnalyzer.isAskUp()) {
                if (doAsk
                        && snapshot.isAskLtLimit()
                        && snapshot.hasAskOnly()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, followUpOrderDuration)) {
                    orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
                    doAsk = false;
                    return;
                }
            }
            if (rateAnalyzer.isBidDown()) {
                if (doBid
                        && snapshot.isBidLtLimit()
                        && snapshot.hasBidOnly()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, followUpOrderDuration)) {
                    orderBid(toNextLot.applyAsInt(snapshot), snapshot);
                    doBid = false;
                    return;
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
                && rateAnalyzer.isReachedAskThresholdWithin(rate, doOrderDuration)) {
            doBid = true;
        }
        if (rateAnalyzer.isBidDown()
                && rateAnalyzer.isReachedBidThresholdWithin(rate, doOrderDuration)) {
            doAsk = true;
        }
    }

    private boolean isAboveStopLossRate(Rate rate) {
        return stopLossRate < rate.getAsk();
    }

    private boolean isBelowStopLossRate(Rate rate) {
        return stopLossRate > rate.getBid();
    }

    private void stopLossProcess(Snapshot snapshot) {
        if (snapshot.hasAsk()) {
            fixAsk(snapshot);
        }
        if (snapshot.hasBid()) {
            fixBid(snapshot);
        }
        recoveryManager.stopLossProcess(snapshot);
        rangeManager.apply();
        rangeManager.print();
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

    @Override
    protected boolean isCalm() {
        return false;
    }

}
