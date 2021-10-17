package autotrade.local.autotrader.impl;

import java.math.BigDecimal;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderEleventh extends AutoTrader {

    private enum OrderDirection {ASK, BID, NONE}

    @AllArgsConstructor
    private enum Term {
         SHORT(Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderEleventh.order.direction.duration.seconds.short")))
        ,LONG(Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderEleventh.order.direction.duration.seconds.long")))
        ;

        @Getter
        private Duration duration;

    }
    private OrderDirection orderDirection;
    private RecoveryManager recoveryManager;
    private Term orderTerm;

    public AutoTraderEleventh() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTraderEleventh.rateAnalizer.threshold.seconds")));
        });
        orderDirection = OrderDirection.NONE;
        orderTerm = Term.SHORT;

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
                orderDirection = OrderDirection.valueOf(AutoTradeUtils.localLoad(Paths.get("localSave", "orderDirection")));
                orderTerm = Term.valueOf(AutoTradeUtils.localLoad(Paths.get("localSave", "orderTerm")));
            }
        };

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), recoveryManager.getSnapshotWhenStart());
                AutoTradeUtils.localSave(Paths.get("localSave", "orderDirection"), orderDirection.name());
                AutoTradeUtils.localSave(Paths.get("localSave", "orderTerm"), orderTerm.name());
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

            orderTerm = Term.SHORT;

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    orderDirection = OrderDirection.ASK;
                    break;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    orderDirection = OrderDirection.BID;
                    break;
                }
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
        case BID_SIDE:
            // 売りポジションが多い場合
        case SAME:
            // ポジションが同数の場合

            if (rateAnalyzer.isAskUp()
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, orderTerm.getDuration())) {
                if (orderDirection == OrderDirection.ASK
                        && orderTerm == Term.LONG) {
                    orderTerm = Term.SHORT;
                }
                if (orderDirection == OrderDirection.BID
                        &&  orderTerm == Term.SHORT) {
                    orderTerm = Term.LONG;
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                }
                orderDirection = OrderDirection.ASK;
            }
            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, orderTerm.getDuration())) {
                if (orderDirection == OrderDirection.BID
                        && orderTerm == Term.LONG) {
                    orderTerm = Term.SHORT;
                }
                if (orderDirection == OrderDirection.ASK
                        &&  orderTerm == Term.SHORT) {
                    orderTerm = Term.LONG;
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                }
                orderDirection = OrderDirection.BID;
            }

            switch (orderDirection) {
            case ASK:
                if (rateAnalyzer.isAskUp()
                        && snapshot.getAskLot() < snapshot.getBidLot()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, Term.LONG.getDuration())) {
                    forceSame(snapshot);
                    break;
                }
                if (rateAnalyzer.isAskUp()
                        && snapshot.getAskLot() < lotManager.getLimit()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(calcLot(snapshot.getAskLot(), snapshot.getBidLot()));
                    break;
                }
                break;
            case BID:
                if (rateAnalyzer.isBidDown()
                        && snapshot.getBidLot() < snapshot.getAskLot()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Term.LONG.getDuration())) {
                    forceSame(snapshot);
                    break;
                }
                if (rateAnalyzer.isBidDown()
                        && snapshot.getBidLot() < lotManager.getLimit()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(calcLot(snapshot.getBidLot(), snapshot.getAskLot()));
                    break;
                }
                break;
            default:
            }

            break;
        default:
        }

    }

    private int calcLot(int targetLot, int otherLot) {
        int lot = 1;
        BigDecimal other = BigDecimal.valueOf(otherLot * 1.5);
        if (targetLot < other.intValue()) {
            lot = 2;
            lot = lot + ((other.intValue() - targetLot) / 10);
        }
        return lot;
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

        if (recoveryManager.isOpen()
                && recoveryManager.isRecoveredWithProfit(snapshot, snapshot.getMargin() / 10000)) {
            fixAll(snapshot);
            return;
        }

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

            Term fixTerm = Term.SHORT;

            if (rateAnalyzer.isBidDown()) {
                if (snapshot.getAskProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, fixTerm.getDuration())) {
                    fixAsk(snapshot);
                    break;
                }
            }

            if (rateAnalyzer.isAskUp()) {
                if (snapshot.getBidProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, fixTerm.getDuration())) {
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
