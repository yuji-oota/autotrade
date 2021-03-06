package autotrade.local.autotrader.impl;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Predicate;

import autotrade.local.actor.MessageListener;
import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AutoTrader;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderEighth extends AutoTrader {

    private RecoveryManager recoveryManager;

    public AutoTraderEighth() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderEighth.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderEighth.rateAnalizer.threshold.seconds")));

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                recoveryManager.open(AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart")));
                log.info("loaded snapshot when recovery start to RecoveryManager {}.", recoveryManager.getSnapshotWhenStart());
                recoveryManager.setCounterTradingSnapshot(null);
            }
        };
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        boolean isOrderable = super.isOrderable(snapshot);

        if (isOrderable) {
            switch (snapshot.getStatus()) {
            case NONE:
            case SAME:
                break;
            case ASK_SIDE:
            case BID_SIDE:
                if (isCalm()) {
                    return false;
                }
                break;
            default:
            }
        }
        return isOrderable;
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        Predicate<Rate> isReachedThreshold;
        Consumer<Snapshot> counterTrading;

        switch (snapshot.getStatus()) {
        case NONE:
            // ??????????????????????????????

            int initialLot = snapshot.getMargin() / 100000;

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), snapshot);
                    return;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), snapshot);
                    return;
                }
            }

            break;
        case ASK_SIDE:
            // ????????????????????????????????????

            if (rateAnalyzer.isBidDown()) {

                isReachedThreshold = r -> rateAnalyzer.isReachedBidThreshold(r);
                counterTrading = s -> orderBid(s.getAskLot() - s.getBidLot() + 1);
                if (snapshot.getAskProfit() < 0) {
                    isReachedThreshold = r -> rateAnalyzer.isReachedBidThresholdWithin(r, Duration.ofMinutes(1));
                }
                if (snapshot.isSpreadWiden()
                        || lotManager.isLimit(snapshot)) {
                    counterTrading = s -> forceSame(s);
                }

                if (isReachedThreshold.test(rate)) {
                    counterTrading.accept(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    return;
                }
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            if (rateAnalyzer.isAskUp()) {

                isReachedThreshold = r -> rateAnalyzer.isReachedAskThreshold(r);
                counterTrading = s -> orderAsk(s.getBidLot() - s.getAskLot() + 1);
                if (snapshot.getBidProfit() < 0) {
                    isReachedThreshold = r -> rateAnalyzer.isReachedAskThresholdWithin(r, Duration.ofMinutes(1));
                }
                if (snapshot.isSpreadWiden()
                        || lotManager.isLimit(snapshot)) {
                    counterTrading = s -> forceSame(s);
                }

                if (isReachedThreshold.test(rate)) {
                    counterTrading.accept(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    return;
                }
            }

            break;
        case SAME:
            // ?????????????????????????????????

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
        switch (snapshot.getStatus()) {
        case NONE:
        case ASK_SIDE:
        case BID_SIDE:
            break;
        case SAME:
            if (snapshot.isSpreadWiden()) {
                return false;
            }
            break;
        default:
        }
        return true;
    }

    @Override
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NONE:
            // ??????????????????????????????

            break;
        case ASK_SIDE:
            // ????????????????????????????????????

            if (snapshot.hasBothSide()) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(10))) {
                    fixBid(snapshot);
                    break;
                }
            }
            if (snapshot.hasOneSide()) {
                if (rateAnalyzer.isBidDown()
                        && recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixAll(snapshot);
                    break;
                }
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            if (snapshot.hasBothSide()) {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(10))) {
                    fixAsk(snapshot);
                    break;
                }
            }
            if (snapshot.hasOneSide()) {
                if (rateAnalyzer.isAskUp()
                        && recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixAll(snapshot);
                    break;
                }
            }

            break;
        case SAME:
            // ?????????????????????????????????

            if (lotManager.isLimit(snapshot)) {
                if (recoveryManager.isRecovered(snapshot)) {
                    fixAll(snapshot);
                    break;
                }
            } else {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(10))) {
                    fixBid(snapshot);
                    break;
                }
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(10))) {
                    fixAsk(snapshot);
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
