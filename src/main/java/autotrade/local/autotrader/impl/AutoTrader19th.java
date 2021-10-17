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
public class AutoTrader19th extends AutoTrader {

    private RecoveryManager recoveryManager;
    private Set<CurrencyPair> recommendedPairs;
    private boolean doAsk;
    private boolean doBid;
    private Duration counterDuration;
    private int dynamicThreshold;
    private int dynamicThresholdOriginally;

    public AutoTrader19th() {
        super();
        log.info("AutoTrader19th started");
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTrader19th.rateAnalizer.threshold.seconds")));
        });
        recommendedPairs = AutoTradeProperties.getList("autoTrader19th.autoRecommended.pairs").stream()
                .map(CurrencyPair::valueOf)
                .collect(Collectors.toSet());
        counterDuration = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader19th.counter.duration.seconds"));

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
                dynamicThresholdOriginally = AutoTradeUtils.localLoad(Paths.get("localSave", "dynamicThresholdOriginally"));
                dynamicThreshold = AutoTradeUtils.localLoad(Paths.get("localSave", "dynamicThreshold"));
            }
        }
        ;

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"),
                            recoveryManager.getSnapshotWhenStart());
                    AutoTradeUtils.localSave(Paths.get("localSave", "dynamicThresholdOriginally"), dynamicThresholdOriginally);
                    AutoTradeUtils.localSave(Paths.get("localSave", "dynamicThreshold"), dynamicThreshold);
                }));
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.isPositionNone();
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

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int initialLot = snapshot.getMargin() / 100000;

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            if (rateAnalyzer.isUpwardWithin(counterDuration)) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doAsk = false;
                    resetDynamicThreshold();
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doBid = false;
                    resetDynamicThreshold();
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
                        } else {
                            orderAsk(1);
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
                        } else {
                            orderBid(1);
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

        if (snapshot.hasAskOnly()) {
            dynamicThreshold = rateAnalyzer.minWithin(counterDuration);
            if (dynamicThreshold > dynamicThresholdOriginally) {
                dynamicThreshold = dynamicThresholdOriginally;
                log.info("dynamicThreshold:{}", dynamicThreshold);
            }
        }
        if (snapshot.hasBidOnly()) {
            dynamicThreshold = rateAnalyzer.maxWithin(counterDuration);
            if (dynamicThreshold < dynamicThresholdOriginally) {
                dynamicThreshold = dynamicThresholdOriginally;
                log.info("dynamicThreshold:{}", dynamicThreshold);
            }
        }
    }

    private void resetDynamicThreshold() {
        dynamicThresholdOriginally = rateAnalyzer.middleWithin(counterDuration);
    }

    private void updateDynamicThreshold(Snapshot snapshot) {
        Rate lastDayBeforeRate = buildLastDayBeforeRate();
        int dynamicThreshold = rateAnalyzer.middleWithin(counterDuration);
        if (snapshot.getRate().isAbobe(lastDayBeforeRate)) {
            if (dynamicThreshold < dynamicThresholdOriginally) {
                dynamicThresholdOriginally = dynamicThreshold;
            }
        } else {
            if (dynamicThreshold > dynamicThresholdOriginally) {
                dynamicThresholdOriginally = dynamicThreshold;
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
    protected boolean isFixable(Snapshot snapshot) {
        boolean isFixable = super.isFixable(snapshot);
        if (isFixable
                && snapshot.isPositionSame()
                && snapshot.isAskGeLimit()) {
            isFixable = false;
        }
        return isFixable;
    }

    @Override
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int targetProfit = snapshot.getMargin() / 10000;

        if (recoveryManager.isOpen()
                && recoveryManager.isRecoveredWithProfit(snapshot, targetProfit)) {
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
                    updateDynamicThreshold(snapshot);
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, counterDuration)) {
                    fixAsk(snapshot);
                    updateDynamicThreshold(snapshot);
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
