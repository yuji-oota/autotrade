package autotrade.local.autotrader.impl;

import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.function.ToIntFunction;

import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AutoTrader;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Market;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * 19thをベースに両建てしないように制御
 *
 */
@Slf4j
public class AutoTrader21th extends AutoTrader {

    private RecoveryManager recoveryManager;
    private boolean doAsk;
    private boolean doBid;
    private Duration stopLossDurationShort;
    private int stopLossRate;
    private int lotLtInitial;
    private int lotGeInitial;
    private ToIntFunction<Snapshot> toProfit;
    private ToIntFunction<Snapshot> toMinimumProfit;
    private ToIntFunction<Snapshot> toInitialLot;

    @SuppressWarnings("unchecked")
    public AutoTrader21th() {
        super();
        log.info("autoTrader21th started.");
        toProfit = (ToIntFunction<Snapshot> & Serializable) s -> new BigDecimal(s.getMargin())
                .multiply(new BigDecimal("0.005")).intValue();
        recoveryManager = new RecoveryManager(toProfit);
        stopLossDurationShort = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader21th.stopLoss.duration.short.seconds"));
        lotLtInitial = AutoTradeProperties.getInt("autoTrader21th.order.lot.ltInitial");
        lotGeInitial = AutoTradeProperties.getInt("autoTrader21th.order.lot.geInitial");
        toInitialLot = s -> toProfit.applyAsInt(s) / 100;
        toMinimumProfit = s -> s.getMargin() / 10000;

        postConstruct();
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
        log.info("snapshotWhenStart:{}", recoveryManager.getSnapshotWhenStart());
        setStopLossRate(AutoTradeUtils.localLoad(Paths.get("localSave", "stopLossRate")));
    }

    @Override
    protected CurrencyPair selectPair() {

        if (recoveryManager.isOpen()) {
            return recoveryManager.getHandlePair();
        }

        changeDisplay(DisplayMode.RATELIST);
        Market market = Market.now();
        // 推奨通貨ペア選択
        return CurrencyPair.getPairs().stream()
                .filter(pair -> pair.getHandleMarket().contains(market.name()))
                .map(pair -> {
                    return new AbstractMap.SimpleEntry<CurrencyPair, Integer>(
                            pair, pairAnalyzerMap.get(pair).rangeWithin(stopLossDurationShort) - pair.getMinSpread());
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

    private int nextLot(Snapshot snapshot) {
        int initialLot = toInitialLot.applyAsInt(snapshot);
        if (initialLot <= snapshot.getMoreLot()) {
            return lotGeInitial;
        }
        int nextLot = initialLot - snapshot.getMoreLot();
        if (nextLot > lotLtInitial) {
            nextLot = lotLtInitial;
        }
        if (initialLot < nextLot) {
            nextLot = initialLot;
        }
        return nextLot;
    }

    @Override
    protected void preFix(Snapshot snapshot) {
        if (recoveryManager.isOpen()
                && (recoveryManager.isBeforeCounterTrading()
                        || recoveryManager.getRecoveryProgress(snapshot) >= 50)) {
            if (snapshot.hasAskOnly()) {
                int minRate = rateAnalyzer.minWithin(stopLossDurationShort);
                if (stopLossRate < minRate) {
                    setStopLossRate(minRate);
                }
            }
            if (snapshot.hasBidOnly()) {
                int maxRate = rateAnalyzer.maxWithin(stopLossDurationShort);
                if (stopLossRate > maxRate) {
                    setStopLossRate(maxRate);
                }
            }
        }
    }

    @Override
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        if (recoveryManager.isRecoveredWithProfit(snapshot)) {
            if (rateAnalyzer.isBidDown()
                    && snapshot.isBidLtAsk()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                fixAll(snapshot);
            }
            if (rateAnalyzer.isAskUp()
                    && snapshot.isBidGtAsk()
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                fixAll(snapshot);
            }
        } else {
            if (isAboveStopLossRate(snapshot.getRate())) {
                if (rateAnalyzer.isAskUp()
                        && snapshot.hasBid()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    if (recoveryManager.isRecoveredWithProfit(snapshot, toMinimumProfit)) {
                        fixAll(snapshot);
                        return;
                    }
                    fixBid(snapshot);
                    stopLossProcess(snapshot);
                    return;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && snapshot.hasAsk()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    if (recoveryManager.isRecoveredWithProfit(snapshot, toMinimumProfit)) {
                        fixAll(snapshot);
                        return;
                    }
                    fixAsk(snapshot);
                    stopLossProcess(snapshot);
                    return;
                }
            }
        }
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

            if (rateAnalyzer.isUpwardWithin(stopLossDurationShort)) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    if (recoveryManager.isClose()) {
                        setStopLossRate(rateAnalyzer.minWithin(stopLossDurationShort));
                        recoveryManager.open(snapshot);
                        orderAsk(toInitialLot.applyAsInt(snapshot), snapshot);
                    } else {
                        setStopLossRate(rateAnalyzer.minWithin(stopLossDurationShort));
                        int lot = Math.max(toInitialLot.applyAsInt(snapshot),
                                recoveryManager.getCounterTradingStartLot());
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        orderAsk(lot, snapshot);
                    }
                    doAsk = false;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    if (recoveryManager.isClose()) {
                        setStopLossRate(rateAnalyzer.maxWithin(stopLossDurationShort));
                        recoveryManager.open(snapshot);
                        orderBid(toInitialLot.applyAsInt(snapshot), snapshot);
                    } else {
                        setStopLossRate(rateAnalyzer.maxWithin(stopLossDurationShort));
                        int lot = Math.max(toInitialLot.applyAsInt(snapshot),
                                recoveryManager.getCounterTradingStartLot());
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        orderBid(lot, snapshot);
                    }
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
                        if (rateAnalyzer.isCalm()) {
                            return;
                        }
                        orderAsk(nextLot(snapshot), snapshot);
                        doAsk = false;
                        return;
                    }
                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        if (rateAnalyzer.isCalm()) {
                            return;
                        }
                        orderBid(nextLot(snapshot), snapshot);
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

    private void setStopLossRate(int rate) {
        stopLossRate = rate;
        log.info("stopLossRate:{}", stopLossRate);
    }

    private boolean isAboveStopLossRate(Rate rate) {
        return stopLossRate < rate.getMiddle();
    }

    private void stopLossProcess(Snapshot snapshot) {
        recoveryManager.stopLossProcess(snapshot);
        snapshot.setFix(true);
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        snapshot.setFix(true);
        recoveryManager.printSummary(snapshot);
        recoveryManager.close();
    }

}
