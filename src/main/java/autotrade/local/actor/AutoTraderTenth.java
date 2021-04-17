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
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderTenth extends AutoTrader {

    private enum OrderDirection {ASK, BID, NONE}
    private OrderDirection orderDirection;
    private RecoveryManager recoveryManager;
    private Duration orderDirectionDuration;

    public AutoTraderTenth() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderTenth.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderTenth.rateAnalizer.threshold.seconds")));
        orderDirectionDuration = Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderTenth.order.direction.duration.seconds"));
        orderDirection = OrderDirection.NONE;

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
            }
        };
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

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), snapshot);
                    orderDirection = OrderDirection.ASK;
                    break;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), snapshot);
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
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, orderDirectionDuration)) {
                if (orderDirection == OrderDirection.BID
                        //&& snapshot.getBidLot() >= lotManager.getLimit()
                        ) {
//                    if (snapshot.hasOneSide()) {
//                        orderAsk(initialLot);
//                    }
//                    fixBid(snapshot);
                }
                orderDirection = OrderDirection.ASK;
            }
            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDirectionDuration)) {
                if (orderDirection == OrderDirection.ASK
                        //&& snapshot.getAskLot() >= lotManager.getLimit()
                        ) {
//                    if (snapshot.hasOneSide()) {
//                        orderBid(initialLot);
//                    }
//                    fixAsk(snapshot);
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
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDirectionDuration)) {
                    fixAsk(snapshot);
                    break;
                }
            }

            if (rateAnalyzer.isAskUp()) {
                if (snapshot.getBidProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, orderDirectionDuration)) {
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
