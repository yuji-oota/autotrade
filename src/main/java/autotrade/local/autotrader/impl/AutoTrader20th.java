package autotrade.local.autotrader.impl;

import java.io.Serializable;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import autotrade.local.actor.MessageListener;
import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AutoTrader;
import autotrade.local.material.CurrencyPair;
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
    private Set<CurrencyPair> recommendedPairs;
    private boolean doAsk;
    private boolean doBid;
    private Duration counterDuration;
    private int dynamicThreshold;
    private int lotLtInitial;
    private int lotGeInitial;
    private Rate firstOrderRate;
    private boolean doResetFirstOrderRate;

    @SuppressWarnings("unchecked")
    public AutoTrader20th() {
        super();
        log.info("autoTrader20th started.");
        recoveryManager = new RecoveryManager((ToIntFunction<Snapshot> & Serializable) s -> {
            int targetProfitByMargin = s.getMargin() / 10000;
            int targetProfitByLot = Math.max(s.getAskLot(), s.getBidLot()) * 10;
            return targetProfitByLot > targetProfitByMargin ? targetProfitByMargin : targetProfitByLot;
        });
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTrader20th.rateAnalizer.threshold.seconds")));
        });
        recommendedPairs = AutoTradeProperties.getList("autoTrader20th.autoRecommended.pairs").stream()
                .map(CurrencyPair::valueOf)
                .collect(Collectors.toSet());
        counterDuration = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader20th.counter.duration.seconds"));
        lotLtInitial = AutoTradeProperties.getInt("autoTrader20th.order.lot.ltInitial");
        lotGeInitial = AutoTradeProperties.getInt("autoTrader20th.order.lot.geInitial");

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                recoveryManager = AutoTradeUtils.localLoad(Paths.get("localSave", "recoveryManager"));
                int threshold = AutoTradeUtils.localLoad(Paths.get("localSave", "dynamicThreshold"));
                firstOrderRate = AutoTradeUtils.localLoad(Paths.get("localSave", "firstOrderRate"));
                setDynamicThreshold(threshold);
            }
        }
        ;

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    AutoTradeUtils.localSave(Paths.get("localSave", "recoveryManager"), recoveryManager);
                    AutoTradeUtils.localSave(Paths.get("localSave", "dynamicThreshold"), dynamicThreshold);
                    AutoTradeUtils.localSave(Paths.get("localSave", "firstOrderRate"), firstOrderRate);
                }));
    }

    private void resetFirstOrderRate(Rate rate) {
        if (doResetFirstOrderRate && rate.isSpreadNarrow()) {
            firstOrderRate = rate;
            doResetFirstOrderRate = false;
            log.info("firstOrderRate is reset:{}", firstOrderRate);
        }
    }

    @Override
    protected void tradePreProcess(Snapshot snapshot) {
        resetFirstOrderRate(snapshot.getRate());
        super.tradePreProcess(snapshot);
    }

    @Override
    protected void tradePostProcess(Snapshot snapshot) {
        if (isSleep(snapshot)) {
            doResetFirstOrderRate = true;
        }
        super.tradePostProcess(snapshot);
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && (snapshot.isPositionNone());
    }

    @Override
    protected void changeRecommended() {
        CurrencyPair recommended = recommendedPairs.stream().map(pair -> {
            return new AbstractMap.SimpleEntry<CurrencyPair, Integer>(
                    pair, Math.abs(AutoTradeUtils.toInt(wrapper.getRateDiffFromList(pair))));
        })
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .get()
                .getKey();
        this.changePair(recommended);
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
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            if (rateAnalyzer.isUpwardWithin(counterDuration)) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    if (recoveryManager.isClose()) {
                        orderAsk(nextLot(snapshot));
                        recoveryManager.open(snapshot);
                        firstOrderRate = rate;
                    } else {
                        orderAsk(recoveryManager.getSnapshotWhenStopLoss().getMoreLot());
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                    }
                    doAsk = false;
                    setDynamicThreshold(rateAnalyzer.minWithin(counterDuration));
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    if (recoveryManager.isClose()) {
                        orderBid(nextLot(snapshot));
                        recoveryManager.open(snapshot);
                        firstOrderRate = rate;
                    } else {
                        orderBid(recoveryManager.getSnapshotWhenStopLoss().getMoreLot());
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                    }
                    doBid = false;
                    setDynamicThreshold(rateAnalyzer.maxWithin(counterDuration));
                }
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
        case BID_SIDE:
            // 売りポジションが多い場合
        case SAME:
            // ポジションが同数の場合

            if (isAboveDynamicThreshold(snapshot.getRate())) {
                if (rateAnalyzer.isAskUp()) {
                    if (doAsk
                            && snapshot.isAskLtLimit()
                            && rateAnalyzer.isReachedAskThreshold(rate)) {
                        if (isCalm()) {
                            return;
                        }
                        orderAsk(nextLot(snapshot));
                        doAsk = false;
                        return;
                    }
                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        if (isCalm()) {
                            return;
                        }
                        orderBid(nextLot(snapshot));
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
    protected void preOrder(Snapshot snapshot) {
        updateDynamicThreshold(snapshot);
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

    private void setDynamicThreshold(int threshold) {
        dynamicThreshold = threshold;
        log.info("dynamicThreshold:{}", dynamicThreshold);
    }

    private void updateDynamicThreshold(Snapshot snapshot) {
        int threshold = 0;
        if (snapshot.hasAskOnly()) {
            threshold = rateAnalyzer.minWithin(counterDuration);
            if (threshold > firstOrderRate.getBid()) {
                threshold = firstOrderRate.getBid();
            }
        }
        if (snapshot.hasBidOnly()) {
            threshold = rateAnalyzer.maxWithin(counterDuration);
            if (threshold < firstOrderRate.getAsk()) {
                threshold = firstOrderRate.getAsk();
            }
        }
        if (threshold <= 0) {
            return;
        }
        if (threshold != dynamicThreshold) {
            setDynamicThreshold(threshold);
        }
    }

    private boolean isAboveDynamicThreshold(Rate rate) {
        return dynamicThreshold < rate.getMiddle();
    }

    @Override
    protected boolean isCalm() {
        return rateAnalyzer.rangeWithin(Duration.ofSeconds(150)) < 25;
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        if (!super.isOrderable(snapshot)) {
            return false;
        }
        if (snapshot.isPositionSame()) {
            return false;
        }
        return true;
    }

    @Override
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        if (snapshot.hasPosition()) {
            if (recoveryManager.isRecoveredWithProfit(snapshot)) {
                if (rateAnalyzer.isBidDown()
                        && snapshot.isPositionAskSide()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    fixAll(snapshot);
                }
                if (rateAnalyzer.isAskUp()
                        && snapshot.isPositionBidSide()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    fixAll(snapshot);
                }
            } else {
                if (isAboveDynamicThreshold(snapshot.getRate())) {
                    if (rateAnalyzer.isAskUp()
                            && rateAnalyzer.isReachedAskThresholdWithin(rate, counterDuration)) {
                        fixBid(snapshot);
                        setDynamicThreshold(rateAnalyzer.minWithin(counterDuration));
                        recoveryManager.setSnapshotWhenStopLoss(snapshot);
                        snapshot.setFix(true);
                    }
                } else {
                    if (rateAnalyzer.isBidDown()
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, counterDuration)) {
                        fixAsk(snapshot);
                        setDynamicThreshold(rateAnalyzer.maxWithin(counterDuration));
                        recoveryManager.setSnapshotWhenStopLoss(snapshot);
                        snapshot.setFix(true);
                    }
                }
            }
        }
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        snapshot.setFix(true);
        recoveryManager.printSummary(snapshot);
        recoveryManager.close();
    }

    @Override
    protected MessageListener customizeMessageListener() {
        MessageListener messageListener = super.customizeMessageListener();
        return messageListener;
    }

}
