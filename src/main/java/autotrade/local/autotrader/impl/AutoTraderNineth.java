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
public class AutoTraderNineth extends AutoTrader {

    private RecoveryManager recoveryManager;
    private Duration fixDuration;

    public AutoTraderNineth() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderNineth.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderNineth.rateAnalizer.threshold.seconds")));
        fixDuration = Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderNineth.fix.duration.seconds"));

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
                if (isCalm()) {
                    isReachedThreshold = r -> rateAnalyzer.isReachedBidThresholdWithin(r, Duration.ofMinutes(10));
                } else {
                    if (snapshot.isSpreadWiden()
                            || lotManager.isLimit(snapshot)) {
                        counterTrading = s -> forceSame(s);
                    }
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
                if (isCalm()) {
                    isReachedThreshold = r -> rateAnalyzer.isReachedAskThresholdWithin(r, Duration.ofMinutes(10));
                } else {
                    if (snapshot.isSpreadWiden()
                            || lotManager.isLimit(snapshot)) {
                        counterTrading = s -> forceSame(s);
                    }
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

            if (snapshot.isSpreadWiden()
                    || lotManager.isLimit(snapshot)) {
                return;
            }

            if (rateAnalyzer.isBidDown()) {

                isReachedThreshold = r -> rateAnalyzer.isReachedBidThreshold(r);
                counterTrading = s -> orderBid(s.getAskLot() - s.getBidLot() + 1);
                if (isReachedThreshold.test(rate)) {
                    counterTrading.accept(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    return;
                }
            }
            if (rateAnalyzer.isAskUp()) {

                isReachedThreshold = r -> rateAnalyzer.isReachedAskThreshold(r);
                counterTrading = s -> orderAsk(s.getBidLot() - s.getAskLot() + 1);
                if (isReachedThreshold.test(rate)) {
                    counterTrading.accept(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    return;
                }
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
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NONE:
            // ??????????????????????????????

            break;
        case ASK_SIDE:
            // ????????????????????????????????????

            if (rateAnalyzer.isBidDown()) {
                if (recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, fixDuration)) {
                    fixAll(snapshot);
                    break;
                }
                if (snapshot.getAskProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    fixAsk(snapshot);
                    break;
                }
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            if (rateAnalyzer.isAskUp()) {
                if (recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, fixDuration)) {
                    fixAll(snapshot);
                    break;
                }
                if (snapshot.getBidProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    fixBid(snapshot);
                    break;
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
