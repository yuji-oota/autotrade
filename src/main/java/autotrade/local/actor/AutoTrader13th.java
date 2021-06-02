package autotrade.local.actor;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader13th extends AutoTrader {

    private enum OrderDirection {ASK, BID, NONE}

    private RecoveryManager recoveryManager;
    private OrderDirection shortOrderDirection;
    private OrderDirection longOrderDirection;
    private Duration shortDuration;
    private Duration longDuration;

    public AutoTrader13th() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTrader13th.rateAnalizer.threshold.seconds")));
        });
        shortOrderDirection = OrderDirection.NONE;
        longOrderDirection = OrderDirection.NONE;
        shortDuration = Duration.ofSeconds(30);
        longDuration = Duration.ofSeconds(600);

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
                shortOrderDirection = OrderDirection.valueOf(AutoTradeUtils.localLoad(Paths.get("localSave", "shortOrderDirection")));
                longOrderDirection = OrderDirection.valueOf(AutoTradeUtils.localLoad(Paths.get("localSave", "longOrderDirection")));
            }
        };

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), recoveryManager.getSnapshotWhenStart());
                AutoTradeUtils.localSave(Paths.get("localSave", "shortOrderDirection"), shortOrderDirection.name());
                AutoTradeUtils.localSave(Paths.get("localSave", "longOrderDirection"), longOrderDirection.name());
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
                shortOrderDirection = OrderDirection.ASK;
                longOrderDirection = OrderDirection.ASK;
            }

            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(initialLot);
                recoveryManager.close();
                recoveryManager.open(snapshot);
                shortOrderDirection = OrderDirection.BID;
                longOrderDirection = OrderDirection.BID;
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
        case BID_SIDE:
            // 売りポジションが多い場合
        case SAME:
            // ポジションが同数の場合

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThresholdWithin(rate, shortDuration)) {
                    if (longOrderDirection == OrderDirection.ASK
                            && shortOrderDirection == OrderDirection.BID
                            && snapshot.getAskLot() < lotManager.getLimit()) {
                        orderAsk(calcLot(snapshot.getAskLot(), snapshot.getBidLot()));
                    }
                    shortOrderDirection = OrderDirection.ASK;
                }
                if (rateAnalyzer.isReachedAskThresholdWithin(rate, longDuration)) {
                    if (longOrderDirection == OrderDirection.BID
                            && snapshot.getAskLot() < snapshot.getBidLot()
                            && snapshot.getAskLot() < lotManager.getLimit()) {
                        orderAsk(calcLot(snapshot.getAskLot(), snapshot.getBidLot()));
                    }
                    longOrderDirection = OrderDirection.ASK;
                }
            }
            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThresholdWithin(rate, shortDuration)) {
                    if (longOrderDirection == OrderDirection.BID
                            && shortOrderDirection == OrderDirection.ASK
                            && snapshot.getBidLot() < lotManager.getLimit()) {
                        orderBid(calcLot(snapshot.getBidLot(), snapshot.getAskLot()));
                    }
                    shortOrderDirection = OrderDirection.BID;
                }
                if (rateAnalyzer.isReachedBidThresholdWithin(rate, longDuration)) {
                    if (longOrderDirection == OrderDirection.ASK
                            && snapshot.getBidLot() < snapshot.getAskLot()
                            && snapshot.getBidLot() < lotManager.getLimit()) {
                        orderBid(calcLot(snapshot.getBidLot(), snapshot.getAskLot()));
                    }
                    longOrderDirection = OrderDirection.BID;
                }
            }

        default:
        }

    }

    private int calcLot(int targetLot, int otherLot) {
        if (targetLot < otherLot) {
            int lotTobe = BigDecimal.valueOf(otherLot * 1.25).intValue();
            if (lotTobe > lotManager.getLimit()) {
                return lotTobe - lotManager.getLimit();
            }
            return lotTobe - targetLot;
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
            if (rateAnalyzer.isBidDown()
                    && snapshot.isPositionAskSide()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, shortDuration)) {
                fixAll(snapshot);
            }
            if (rateAnalyzer.isAskUp()
                    && snapshot.isPositionBidSide()
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, shortDuration)) {
                fixAll(snapshot);
            }
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
