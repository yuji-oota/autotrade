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
public class AutoTrader17th extends AutoTrader {

    private enum OrderDirection {ASK, BID, NONE}

    private RecoveryManager recoveryManager;
    private Set<CurrencyPair> recommendedPairs;
    private boolean doAsk;
    private boolean doBid;
    private Duration counterLessThanDuration;
    private Duration counterDuration;
    private OrderDirection orderDirection;

    public AutoTrader17th() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTrader17th.rateAnalizer.threshold.seconds")));
        });
        recommendedPairs = AutoTradeProperties.getList("autoTrader17th.autoRecommended.pairs").stream()
                .map(CurrencyPair::valueOf)
                .collect(Collectors.toSet());
        counterLessThanDuration = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader17th.counter.lessThanDuration.seconds"));
        counterDuration = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader17th.counter.duration.seconds"));
        orderDirection = OrderDirection.NONE;

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
            }
        };

        //??????????????????????????????????????????????????????
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), recoveryManager.getSnapshotWhenStart());
            }
        ));
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.isPositionNone();
    }

    @Override
    protected void initialize() {
        super.initialize();
    }

    @Override
    protected void changeRecommended() {
        CurrencyPair recommended = recommendedPairs.stream().map(pair ->{
            return new AbstractMap.SimpleEntry<CurrencyPair, Integer>(
                    pair, Math.abs(AutoTradeUtils.toInt(wrapper.getRateDiffFromList(pair))));
        })
        .max(Comparator.comparingInt(Map.Entry::getValue))
        .get()
        .getKey();
        this.changePair(recommended);
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        boolean isOrderable = super.isOrderable(snapshot);
        if (isOrderable) {
            if (isCalm()) {
                isOrderable = false;
            }
        }
        return isOrderable;
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int initialLot = snapshot.getMargin() / 100000;

        switch (snapshot.getStatus()) {
        case NONE:
            // ??????????????????????????????

            if (rateAnalyzer.isUpwardWithin(counterDuration)) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(calcLot(initialLot, snapshot.getAskLot(), snapshot.getBidLot()));
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doAsk = false;
                    orderDirection = OrderDirection.ASK;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(calcLot(initialLot, snapshot.getBidLot(), snapshot.getAskLot()));
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doBid = false;
                    orderDirection = OrderDirection.BID;
                }
            }


            break;
        case ASK_SIDE:
            // ????????????????????????????????????
        case BID_SIDE:
            // ????????????????????????????????????
        case SAME:
            // ?????????????????????????????????

            if (orderDirection == OrderDirection.NONE) {
                orderDirection = OrderDirection.ASK;
                if (snapshot.isBidGtAsk()) {
                    orderDirection = OrderDirection.BID;
                }
            }

            if (isUpward(rate)) {
                if (rateAnalyzer.isAskUp()) {
                    if (doAsk
                            && snapshot.isAskGeBid()
                            && snapshot.getAskLot() < lotManager.getLimit()
                            && rateAnalyzer.isReachedAskThreshold(rate)) {
                        orderAsk(calcLot(initialLot, snapshot.getAskLot(), snapshot.getBidLot()));
                        doAsk = false;
                        return;
                    }
                    if (snapshot.isAskLtBid()
                            && rateAnalyzer.isReachedAskThresholdWithin(rate, counterLessThanDuration)) {
                        orderAsk(calcLot(initialLot, snapshot.getAskLot(), snapshot.getBidLot()));
                        return;
                    }
                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidGeAsk()
                            && snapshot.getBidLot() < lotManager.getLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        orderBid(calcLot(initialLot, snapshot.getBidLot(), snapshot.getAskLot()));
                        doBid = false;
                        return;
                    }
                    if (snapshot.isBidLtAsk()
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, counterLessThanDuration)) {
                        orderBid(calcLot(initialLot, snapshot.getBidLot(), snapshot.getAskLot()));
                        return;
                    }
                }
            }

            if (rateAnalyzer.isAskUp()
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                doBid = true;
            }
            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                doAsk = true;
            }

        default:
        }

    }

    private static int calcLot(int initialLot, int lot, int counter) {
        int target = initialLot < counter ? counter + initialLot : initialLot;
        if (lot < target) {
            int diff = target - lot;
            if (diff <= 10) {
                return diff;
            } else {
                return 10;
            }
        }
        return 1;
    }

    private boolean isUpward(Rate rate) {
        if (rateAnalyzer.isAskUp()
                && orderDirection == OrderDirection.BID // NOTE:????????????????????????????????????????????????
                && rateAnalyzer.isReachedAskThreshold(rate) // NOTE:????????????????????????????????????????????????
                && rateAnalyzer.isReachedAskThresholdWithin(rate, counterDuration)) {
            orderDirection = OrderDirection.ASK;
        }
        if (rateAnalyzer.isBidDown()
                && orderDirection == OrderDirection.ASK // NOTE:????????????????????????????????????????????????
                && rateAnalyzer.isReachedBidThreshold(rate) // NOTE:????????????????????????????????????????????????
                && rateAnalyzer.isReachedBidThresholdWithin(rate, counterDuration)) {
            orderDirection = OrderDirection.BID;
        }
        return orderDirection == OrderDirection.ASK;
    }

    @Override
    protected boolean isCalm() {
        return rateAnalyzer.rangeWithin(Duration.ofSeconds(150)) < 25;
    }

    @Override
    protected boolean isFixable(Snapshot snapshot) {
        boolean isFixable = super.isFixable(snapshot);
        if (isFixable
                && snapshot.isPositionSame()
                && lotManager.isLimit(snapshot)) {
            isFixable = false;
        }
        return isFixable;
    }

    @Override
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int targetProfit = snapshot.getMargin() / 10000;

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

        if (snapshot.hasBothSide()
                && rateAnalyzer.isBidDown()
                && rateAnalyzer.isReachedBidThreshold(rate)) {
            if (targetProfit <= snapshot.getAskProfit()) {
                fixAsk(snapshot);
                return;
            }
        }
        if (snapshot.hasBothSide()
                && rateAnalyzer.isAskUp()
                && rateAnalyzer.isReachedAskThreshold(rate)) {
            if (targetProfit <= snapshot.getBidProfit()) {
                fixBid(snapshot);
                return;
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
