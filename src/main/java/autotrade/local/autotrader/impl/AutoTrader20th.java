package autotrade.local.autotrader.impl;

import java.io.Serializable;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;
import java.util.function.ToIntFunction;

import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AutoTrader;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.DisplayMode;
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
public class AutoTrader20th extends AutoTrader {

    private RecoveryManager recoveryManager;
    private boolean doAsk;
    private boolean doBid;
    private Duration stopLossDurationShort;
    private Duration stopLossDurationLong;
    private int dynamicThreshold;
    private int lotLtInitial;
    private int lotGeInitial;

    @SuppressWarnings("unchecked")
    public AutoTrader20th() {
        super();
        log.info("autoTrader20th started.");
        recoveryManager = new RecoveryManager((ToIntFunction<Snapshot> & Serializable) s -> {
            int targetProfitByMargin = s.getMargin() / 10000;
            int targetProfitByLot = s.getMoreLot() * 10;
            return targetProfitByLot > targetProfitByMargin ? targetProfitByMargin : targetProfitByLot;
        });
        stopLossDurationShort = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader20th.stopLoss.duration.short.seconds"));
        stopLossDurationLong = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader20th.stopLoss.duration.long.seconds"));
        lotLtInitial = AutoTradeProperties.getInt("autoTrader20th.order.lot.ltInitial");
        lotGeInitial = AutoTradeProperties.getInt("autoTrader20th.order.lot.geInitial");

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                loadLocal();
            }
        }

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(() -> saveLocal()));
    }

    @Override
    protected void saveLocal() {
        AutoTradeUtils.localSave(Paths.get("localSave", "recoveryManager"), recoveryManager);
        AutoTradeUtils.localSave(Paths.get("localSave", "dynamicThreshold"), dynamicThreshold);
    }

    @Override
    protected void loadLocal() {
        recoveryManager = AutoTradeUtils.localLoad(Paths.get("localSave", "recoveryManager"));
        log.info("snapshotWhenStart:{}", recoveryManager.getSnapshotWhenStart());
        log.info("snapshotWhenStopLoss:{}", recoveryManager.getSnapshotWhenStopLoss());
        setDynamicThreshold(AutoTradeUtils.localLoad(Paths.get("localSave", "dynamicThreshold")));
    }

    @Override
    protected CurrencyPair selectPair() {

        if (recoveryManager.isOpen()) {
            return recoveryManager.getHandlePair();
        }

        if (orderablePairs.size() == 1) {
            return orderablePairs.get(0);
        }

        changeDisplay(DisplayMode.RATELIST);
        // 推奨通貨ペア選択
        return orderablePairs.stream()
                .map(pair -> {
                    return new AbstractMap.SimpleEntry<CurrencyPair, Integer>(
                            pair, pairAnalyzerMap.get(pair).rangeWithin(stopLossDurationShort));
                })
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .get()
                .getKey();
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && (snapshot.hasNoPosition());
    }

    private int nextLot(Snapshot snapshot) {
        int initialLot = snapshot.getMargin() / 100000;
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
                        || recoveryManager.isReachedRecover())) {
            if (snapshot.hasAskOnly()) {
                int threshold = rateAnalyzer.minWithin(stopLossDurationShort);
                if (dynamicThreshold < threshold) {
                    setDynamicThreshold(threshold);
                }
            }
            if (snapshot.hasBidOnly()) {
                int threshold = rateAnalyzer.maxWithin(stopLossDurationShort);
                if (dynamicThreshold > threshold) {
                    setDynamicThreshold(threshold);
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
            if (isAboveDynamicThreshold(snapshot.getRate())) {
                if (rateAnalyzer.isAskUp()
                        && snapshot.hasBid()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    fixBid(snapshot);
                    stopLossProcess(snapshot);
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && snapshot.hasAsk()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    fixAsk(snapshot);
                    stopLossProcess(snapshot);
                }
            }
            if (recoveryManager.getRecoveryProgress(snapshot) >= 50) {
                if (rateAnalyzer.isAskUp()
                        && snapshot.hasBid()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, stopLossDurationShort)) {
                    fixBid(snapshot);
                    stopLossProcess(snapshot);
                }
                if (rateAnalyzer.isBidDown()
                        && snapshot.hasAsk()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, stopLossDurationShort)) {
                    fixAsk(snapshot);
                    stopLossProcess(snapshot);
                }
            }
            if (snapshot.hasProfit()) {
                if (rateAnalyzer.isAskUp()
                        && snapshot.hasBid()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, stopLossDurationLong)) {
                    fixBid(snapshot);
                    stopLossProcess(snapshot);
                }
                if (rateAnalyzer.isBidDown()
                        && snapshot.hasAsk()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, stopLossDurationLong)) {
                    fixAsk(snapshot);
                    stopLossProcess(snapshot);
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
                        orderAsk(nextLot(snapshot), snapshot);
                        recoveryManager.open(snapshot);
                        setDynamicThreshold(rateAnalyzer.minWithin(stopLossDurationShort));
                    } else {
                        if (recoveryManager.isFixWithProfit()) {
                            orderAsk(nextLot(snapshot), snapshot);
                        } else {
                            orderAsk(recoveryManager.getSnapshotWhenStopLoss().getMoreLot(), snapshot);
                        }
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        setDynamicThreshold(rateAnalyzer.minWithin(stopLossDurationLong));
                    }
                    doAsk = false;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    if (recoveryManager.isClose()) {
                        orderBid(nextLot(snapshot), snapshot);
                        recoveryManager.open(snapshot);
                        setDynamicThreshold(rateAnalyzer.maxWithin(stopLossDurationShort));
                    } else {
                        if (recoveryManager.isFixWithProfit()) {
                            orderBid(nextLot(snapshot), snapshot);
                        } else {
                            orderBid(recoveryManager.getSnapshotWhenStopLoss().getMoreLot(), snapshot);
                        }
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        setDynamicThreshold(rateAnalyzer.maxWithin(stopLossDurationLong));
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

            if (isAboveDynamicThreshold(snapshot.getRate())) {
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

    private void setDynamicThreshold(int threshold) {
        dynamicThreshold = threshold;
        log.info("dynamicThreshold:{}", dynamicThreshold);
    }

    private boolean isAboveDynamicThreshold(Rate rate) {
        return dynamicThreshold < rate.getMiddle();
    }

    private void stopLossProcess(Snapshot snapshot) {
        recoveryManager.printSummary(snapshot);
        recoveryManager.setSnapshotWhenStopLoss(snapshot);
        recoveryManager.setFixWithProfit(snapshot.hasProfit());
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
