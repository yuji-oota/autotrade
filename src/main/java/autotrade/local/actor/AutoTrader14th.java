package autotrade.local.actor;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader14th extends AutoTrader {

    private enum OrderDirection {ASK, BID, NONE}

    private RecoveryManager recoveryManager;
    private OrderDirection orderDirection;

    public AutoTrader14th() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTrader14th.rateAnalizer.threshold.seconds")));
        });

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
            }
        };

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), recoveryManager.getSnapshotWhenStart());
            }
        ));
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        boolean isOrderable = super.isOrderable(snapshot);
        return isOrderable;
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int initialLot = snapshot.getMargin() / 100000;

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            if (rateAnalyzer.isAskUp()
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(initialLot);
                recoveryManager.close();
                recoveryManager.open(snapshot);
                orderDirection = OrderDirection.ASK;
            }

            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(initialLot);
                recoveryManager.close();
                recoveryManager.open(snapshot);
                orderDirection = OrderDirection.BID;
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
        case BID_SIDE:
            // 売りポジションが多い場合
        case SAME:
            // ポジションが同数の場合

            if (rateAnalyzer.isAskUp()) {
                if (snapshot.getAskLot() >= snapshot.getBidLot()
                        && snapshot.getAskProfit() >= 0
                        && orderDirection == OrderDirection.BID
                        && snapshot.getAskLot() < lotManager.getLimit()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    if (snapshot.getAskLot() == snapshot.getBidLot()) {
                        orderAsk(initialLot / 2);
                    } else {
                        orderAsk(1);
                    }
                    orderDirection = OrderDirection.ASK;
                }
                if (snapshot.getAskLot() < snapshot.getBidLot()
                        && snapshot.getBidProfit() < 0
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    forceSame(snapshot);
                }
                if (rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderDirection = OrderDirection.ASK;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (snapshot.getAskLot() <= snapshot.getBidLot()
                        && snapshot.getBidProfit() >= 0
                        && orderDirection == OrderDirection.ASK
                        && snapshot.getBidLot() < lotManager.getLimit()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    if (snapshot.getAskLot() == snapshot.getBidLot()) {
                        orderBid(initialLot / 2);
                    } else {
                        orderBid(1);
                    }
                    orderDirection = OrderDirection.BID;
                }
                if (snapshot.getAskLot() > snapshot.getBidLot()
                        && snapshot.getAskProfit() < 0
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    forceSame(snapshot);
                }
                if (rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderDirection = OrderDirection.BID;
                }
            }

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
                && lotManager.isLimit(snapshot)) {
            isFixable = false;
        }
        return isFixable;
    }

    @Override
    protected void fix(Snapshot snapshot) {

//        Rate rate = snapshot.getRate();

        if (recoveryManager.isOpen()
                && recoveryManager.isRecoveredWithProfit(snapshot, snapshot.getMargin() / 10000)) {
            fixAll(snapshot);
//            if (rateAnalyzer.isBidDown()
//                    && snapshot.isPositionAskSide()
//                    && rateAnalyzer.isReachedBidThreshold(rate)) {
//                fixAll(snapshot);
//            }
//            if (rateAnalyzer.isAskUp()
//                    && snapshot.isPositionBidSide()
//                    && rateAnalyzer.isReachedAskThreshold(rate)) {
//                fixAll(snapshot);
//            }
            return;
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
