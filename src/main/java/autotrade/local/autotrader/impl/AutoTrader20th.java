package autotrade.local.autotrader.impl;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
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

    public AutoTrader20th() {
        super();
        log.info("autoTrader20th started.");
        recoveryManager = new RecoveryManager(s -> {
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
                Snapshot snapshotWhenRecoveryStart = AutoTradeUtils
                        .localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshotWhenRecoveryStart);
                Snapshot counterTradingSnapshot = AutoTradeUtils
                        .localLoad(Paths.get("localSave", "counterTradingSnapshot"));
                recoveryManager.setCounterTradingSnapshot(counterTradingSnapshot);
                int threshold = AutoTradeUtils.localLoad(Paths.get("localSave", "dynamicThreshold"));
                firstOrderRate = AutoTradeUtils.localLoad(Paths.get("localSave", "firstOrderRate"));
                setDynamicThreshold(threshold);
            }
        }
        ;

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"),
                            recoveryManager.getSnapshotWhenStart());
                    AutoTradeUtils.localSave(Paths.get("localSave", "counterTradingSnapshot"),
                            recoveryManager.getCounterTradingSnapshot());
                    AutoTradeUtils.localSave(Paths.get("localSave", "dynamicThreshold"), dynamicThreshold);
                    AutoTradeUtils.localSave(Paths.get("localSave", "firstOrderRate"), firstOrderRate);
                }));
    }

    @Override
    protected void tradePostProcess(Snapshot snapshot) {
        if (isInactiveTime()
                && snapshot.isPositionSame()) {
            doResetFirstOrderRate = true;
        }
        super.tradePostProcess(snapshot);
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && (snapshot.isPositionNone() || snapshot.isPositionSame());
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
                    orderAsk(nextLot(snapshot));
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doAsk = false;
                    setDynamicThreshold(rateAnalyzer.minWithin(counterDuration));
                    firstOrderRate = rate;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(nextLot(snapshot));
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doBid = false;
                    setDynamicThreshold(rateAnalyzer.maxWithin(counterDuration));
                    firstOrderRate = rate;
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
                        if (snapshot.isAskLtBid()) {
                            forceSame(snapshot);
                            recoveryManager.setCounterTradingSnapshot(snapshot);
                        } else {
                            if (isCalm()) {
                                return;
                            }
                            orderAsk(nextLot(snapshot));
                        }
                        doAsk = false;
                        return;
                    }
                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        if (snapshot.isBidLtAsk()) {
                            forceSame(snapshot);
                            recoveryManager.setCounterTradingSnapshot(snapshot);
                        } else {
                            if (isCalm()) {
                                return;
                            }
                            orderBid(nextLot(snapshot));
                        }
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

    private void resetFirstOrderRate(Rate rate) {
        if (doResetFirstOrderRate) {
            firstOrderRate = rate;
            doResetFirstOrderRate = false;
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
        }
        if (snapshot.hasBidOnly()) {
            threshold = rateAnalyzer.maxWithin(counterDuration);
        }
        if (threshold <= 0) {
            return;
        }
        if (recoveryManager.isOpen() && recoveryManager.isBeforeCounterTrading()) {
            if (snapshot.hasAskOnly()
                    && threshold > dynamicThreshold) {
                setDynamicThreshold(threshold);
            }
            if (snapshot.hasBidOnly()
                    && threshold < dynamicThreshold) {
                setDynamicThreshold(threshold);
            }
        }
        if (snapshot.getRate().isAbobe(firstOrderRate)) {
            if (snapshot.hasBidOnly() && threshold < dynamicThreshold) {
                setDynamicThreshold(threshold);
            }
        } else {
            if (snapshot.hasAskOnly() && threshold > dynamicThreshold) {
                setDynamicThreshold(threshold);
            }
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
    protected boolean isFixable(Snapshot snapshot) {
        if (!super.isFixable(snapshot)) {
            return false;
        }
        if (snapshot.isPositionSame()) {
            if (snapshot.isAskGeLimit()) {
                return false;
            }
            if (isNearIndicator()) {
                return false;
            }
            if (snapshot.isSpreadWiden()) {
                return false;
            }
            if (isCalm()) {
                return false;
            }
            if (isInactiveTime()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        if (recoveryManager.isOpen()
                && recoveryManager.isRecoveredWithProfit(snapshot)) {
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
            return;
        }

        if (snapshot.hasBothSide()) {
            if (isAboveDynamicThreshold(snapshot.getRate())) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, counterDuration)) {
                    fixBid(snapshot);
                    setDynamicThreshold(rateAnalyzer.minWithin(counterDuration));
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, counterDuration)) {
                    fixAsk(snapshot);
                    setDynamicThreshold(rateAnalyzer.maxWithin(counterDuration));
                }
            }
        }
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        recoveryManager.printSummary(snapshot);
        recoveryManager.close();
    }

    @Override
    protected MessageListener customizeMessageListener() {
        MessageListener messageListener = super.customizeMessageListener();
        return messageListener;
    }

}
