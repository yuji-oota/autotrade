package autotrade.local.autotrader.impl;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;

import autotrade.local.actor.MessageListener;
import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AutoTrader;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderTwelfth extends AutoTrader {

    private enum OrderDirection {ASK, BID, NONE}

    private OrderDirection lastOrderDirection;
    private RecoveryManager recoveryManager;

    public AutoTraderTwelfth() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTraderTwelfth.rateAnalizer.threshold.seconds")));
        });
        lastOrderDirection = OrderDirection.NONE;

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
                lastOrderDirection = OrderDirection.valueOf(AutoTradeUtils.localLoad(Paths.get("localSave", "lastOrderDirection")));
            }
        };

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), recoveryManager.getSnapshotWhenStart());
                AutoTradeUtils.localSave(Paths.get("localSave", "lastOrderDirection"), lastOrderDirection.name());
            }
        ));
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        boolean isOrderable = super.isOrderable(snapshot);
        if (isOrderable
                && snapshot.hasPosition()
                && recoveryManager.isOpen()
                && recoveryManager.isRecovered(snapshot)) {
            isOrderable = false;
        }
        if (isOrderable
                && snapshot.isPositionNone()
                && isInactiveTime()) {
            isOrderable = false;
        }
        return isOrderable;
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int initialLot = snapshot.getMargin() / 100000;

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    lastOrderDirection = OrderDirection.ASK;
                    break;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    lastOrderDirection = OrderDirection.BID;
                    break;
                }
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
        case BID_SIDE:
            // 売りポジションが多い場合

            if (snapshot.getAskLot() == 0) {
                lastOrderDirection = OrderDirection.BID;
            }
            if (snapshot.getBidLot() == 0) {
                lastOrderDirection = OrderDirection.ASK;
            }

            switch (lastOrderDirection) {
            case BID:
                if (rateAnalyzer.isAskUp()
                        && snapshot.getAskLot() < lotManager.getLimit()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(calcLot(snapshot.getAskLot(), snapshot.getBidLot()));
                    lastOrderDirection = OrderDirection.ASK;
                    break;
                }
                break;
            case ASK:
                if (rateAnalyzer.isBidDown()
                        && snapshot.getBidLot() < lotManager.getLimit()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(calcLot(snapshot.getBidLot(), snapshot.getAskLot()));
                    lastOrderDirection = OrderDirection.BID;
                    break;
                }
                break;
            default:
            }

            break;
        case SAME:
            // ポジションが同数の場合

            if (rateAnalyzer.isAskUp()
                    && snapshot.getAskLot() < lotManager.getLimit()
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(10))) {
                orderAsk(calcLot(snapshot.getAskLot(), snapshot.getBidLot()));
                lastOrderDirection = OrderDirection.ASK;
                break;
            }
            if (rateAnalyzer.isBidDown()
                    && snapshot.getBidLot() < lotManager.getLimit()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(10))) {
                orderBid(calcLot(snapshot.getBidLot(), snapshot.getAskLot()));
                lastOrderDirection = OrderDirection.BID;
                break;
            }

            break;
        default:
        }

    }

    private int calcLot(int targetLot, int otherLot) {
        if (targetLot == 0) {
            return otherLot;
        }
        return 1;
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
                && isCalm()) {
            isFixable = false;
        }
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

//        if (recoveryManager.isOpen()
//                && recoveryManager.isRecoveredWithProfit(snapshot, snapshot.getMargin() / 10000)) {
//            fixAll(snapshot);
//            return;
//        }

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
        case BID_SIDE:
            // 売りポジションが多い場合
        case SAME:
            // ポジションが同数の場合

            if (recoveryManager.isRecoveredWithProfit(snapshot, snapshot.getMargin() / 10000)) {
                if (rateAnalyzer.isBidDown()
                        && snapshot.isPositionAskSide()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofSeconds(30))) {
                    fixAll(snapshot);
                }
                if (rateAnalyzer.isAskUp()
                        && snapshot.isPositionBidSide()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofSeconds(30))) {
                    fixAll(snapshot);
                }
                break;
            }

            if (rateAnalyzer.isBidDown()) {
                if (snapshot.getAskProfit() >= 0
                        && snapshot.getAskLot() > 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    fixAsk(snapshot);
                    break;
                }
            }

            if (rateAnalyzer.isAskUp()) {
                if (snapshot.getBidProfit() >= 0
                        && snapshot.getBidLot() > 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    fixBid(snapshot);
                    break;
                }
            }
            break;

        default:
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
        messageListener.putCommand(ReservedMessage.CLOSERECOVERYMANAGER, (args) -> recoveryManager.close());
        return messageListener;
    }

}
