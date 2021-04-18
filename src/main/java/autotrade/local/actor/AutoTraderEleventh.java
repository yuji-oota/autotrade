package autotrade.local.actor;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;

import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.material.CurrencyPair;
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
    private enum OrderTerm {
         SHORT(Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderEleventh.order.direction.duration.seconds.short")))
        ,LONG(Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderEleventh.order.direction.duration.seconds.long")))
        ;

        @Getter
        private Duration duration;

        public OrderTerm change() {
            return this == SHORT ? LONG : SHORT;
        }

    }
    private OrderDirection orderDirection;
    private RecoveryManager recoveryManager;
    private OrderTerm orderTerm;

    public AutoTraderEleventh() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderEleventh.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderEleventh.rateAnalizer.threshold.seconds")));
        orderDirection = OrderDirection.NONE;
        orderTerm = OrderTerm.SHORT;

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
                orderDirection = OrderDirection.valueOf(AutoTradeUtils.localLoad(Paths.get("localSave", "orderDirection")));
                orderTerm = OrderTerm.valueOf(AutoTradeUtils.localLoad(Paths.get("localSave", "orderTerm")));
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
        return isOrderable;
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int initialLot = snapshot.getMargin() / 100000;

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            orderTerm = OrderTerm.SHORT;

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThresholdWithin(rate, orderTerm.getDuration())) {
                    orderAsk(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    orderDirection = OrderDirection.ASK;
                    break;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThresholdWithin(rate, orderTerm.getDuration())) {
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
                if (orderDirection == OrderDirection.BID) {
                    orderTerm = orderTerm.change();
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                }
                orderDirection = OrderDirection.ASK;
            }
            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, orderTerm.getDuration())) {
                if (orderDirection == OrderDirection.ASK) {
                    orderTerm = orderTerm.change();
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                }
                orderDirection = OrderDirection.BID;
            }

            switch (orderDirection) {
            case ASK:
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThreshold(rate)
                        && snapshot.getAskLot() < lotManager.getLimit()) {
                    orderAsk(snapshot.getAskLot() == 0 ? initialLot : 1);
                    break;
                }
                break;
            case BID:
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)
                        && snapshot.getBidLot() < lotManager.getLimit()) {
                    orderBid(snapshot.getBidLot() == 0 ? initialLot : 1);
                    break;
                }
                break;
            default:
            }

            break;
        default:
        }

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

            if (rateAnalyzer.isBidDown()) {
                if (snapshot.getAskProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, OrderTerm.LONG.getDuration())) {
                    fixAsk(snapshot);
                    break;
                }
            }

            if (rateAnalyzer.isAskUp()) {
                if (snapshot.getBidProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, OrderTerm.LONG.getDuration())) {
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
