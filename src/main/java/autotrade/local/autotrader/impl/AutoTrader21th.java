package autotrade.local.autotrader.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.function.ToIntFunction;

import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AbstractAutoTrader;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 20thから派生
 *
 */
@Slf4j
public class AutoTrader21th extends AbstractAutoTrader {

    private RecoveryManager recoveryManager;
    private boolean doAsk;
    private boolean doBid;
    private Duration stopLossDuration;
    private int stopLossRate;
    private int lotGeInitial;
    private ToIntFunction<Snapshot> toProfit;
    private ToIntFunction<Snapshot> toMinimumProfit;
    private ToIntFunction<Snapshot> toInitialLot;

    @SuppressWarnings("unchecked")
    public AutoTrader21th() {
        super();
        log.info("autoTrader21th started.");
        toProfit = (ToIntFunction<Snapshot> & Serializable) s -> new BigDecimal(s.getMargin())
                .multiply(s.getPair().getProfitMagnification()).intValue();
        recoveryManager = new RecoveryManager(toProfit);
        stopLossDuration = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader21th.stopLoss.duration.seconds"));
        lotGeInitial = AutoTradeProperties.getInt("autoTrader21th.order.lot.geInitial");
        toInitialLot = s -> toProfit.applyAsInt(s) / 100;
        toMinimumProfit = s -> s.getMargin() / 10000;
    }

    @Override
    protected void saveLocal() {
        super.saveLocal();
        AutoTradeUtils.localSave(Paths.get("localSave", "recoveryManager"), recoveryManager);
        AutoTradeUtils.localSave(Paths.get("localSave", "stopLossRate"), stopLossRate);
    }

    @Override
    protected void loadLocal() {
        super.loadLocal();
        recoveryManager = AutoTradeUtils.localLoad(Paths.get("localSave", "recoveryManager"));
        log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
        stopLossRate = AutoTradeUtils.localLoad(Paths.get("localSave", "stopLossRate"));
    }

    @Override
    protected CurrencyPair selectPair() {

        if (recoveryManager.isOpen()) {
            return recoveryManager.getHandlePair();
        }

        changeDisplay(DisplayMode.RATELIST);
        // 推奨通貨ペア選択
        LocalTime now = LocalTime.now();
        return CurrencyPair.getPairs().stream()
                .filter(pair -> pair.isHandleable(now))
                .map(pair -> {
                    return new AbstractMap.SimpleEntry<CurrencyPair, Integer>(
                            pair, pairAnalyzerMap.get(pair).rangeWithin(stopLossDuration) - pair.getMinSpread());
                })
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .get()
                .getKey();
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.hasNoPosition();
    }

    @Override
    protected void preFix(Snapshot snapshot) {
        if (recoveryManager.isOpen()
                && (recoveryManager.isBeforeCounterTrading()
                        || recoveryManager.getRecoveryProgress(snapshot) >= 50)) {
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
        }
    }

    @Override
    protected boolean fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        if (indicatorManager.isNextImportant()
                && indicatorManager.isNextIndicatorWithin(Duration.ofSeconds(90))) {
            stopLossProcess(snapshot);
            return true;
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
                && recoveryManager.getHandlePair() != snapshot.getPair()) {
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
                        orderAsk(toInitialLot.applyAsInt(snapshot), snapshot);
                    } else {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        orderAsk(
                                Math.max(toInitialLot.applyAsInt(snapshot),
                                        recoveryManager.getCounterTradingStartLot()),
                                snapshot);
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
                        orderBid(toInitialLot.applyAsInt(snapshot), snapshot);
                    } else {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        orderBid(
                                Math.max(toInitialLot.applyAsInt(snapshot),
                                        recoveryManager.getCounterTradingStartLot()),
                                snapshot);
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
                        orderAsk(lotGeInitial, snapshot);
                        doAsk = false;
                        return;
                    }
                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        orderBid(lotGeInitial, snapshot);
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

    @Override
    protected void postTrade(Snapshot snapshot) {
        if (recoveryManager.isOpen()
                && isSleep(snapshot)) {
            saveLocal();
        }
        super.postTrade(snapshot);
    }

    private boolean isAboveStopLossRate(Rate rate) {
        return stopLossRate < rate.getMiddle();
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
