package autotrade.local.autotrader.impl;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Predicate;

import autotrade.local.actor.MessageListener;
import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.actor.Messenger;
import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AutoTrader;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderSeventh extends AutoTrader {

    private RecoveryManager recoveryManager;

    public AutoTraderSeventh() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderSeventh.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderSeventh.rateAnalizer.threshold.seconds")));

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
    protected void cloudSave() {
        super.cloudSave();

        Messenger.set(
                "snapshotWhenRecoveryStart",
                Base64.getEncoder().encodeToString(AutoTradeUtils.serialize(recoveryManager.getSnapshotWhenStart())));
        log.info("saved snapshot when recovery start {}.", recoveryManager.getSnapshotWhenStart());

        Messenger.set(
                "counterTradingSnapshot",
                Base64.getEncoder().encodeToString(AutoTradeUtils.serialize(recoveryManager.getCounterTradingSnapshot())));
        log.info("saved countertrading rate {}.", recoveryManager.getCounterTradingSnapshot());
    }

    @Override
    protected void cloudLoad() {
        super.cloudLoad();

        recoveryManager.open(
                AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("snapshotWhenRecoveryStart"))));
        log.info("loaded snapshot when recovery start to RecoveryManager {}.", recoveryManager.getSnapshotWhenStart());

        recoveryManager.setCounterTradingSnapshot(
                AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("counterTradingSnapshot"))));
        log.info("loaded saved countertrading rate to RecoveryManager {}.", recoveryManager.getCounterTradingSnapshot());

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

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThreshold(rate)
                        || rateAnalyzer.isReachedAverageAsk(rate)) {
                    orderAsk(snapshot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), snapshot);
                    return;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThreshold(rate)
                        || rateAnalyzer.isReachedAverageBid(rate)) {
                    orderBid(snapshot);
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
                counterTrading = s -> orderBid(s);
                if (snapshot.isSpreadWiden()) {
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
                counterTrading = s -> orderAsk(s);
                if (snapshot.isSpreadWiden()) {
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

        if (snapshot.hasPosition()
                && recoveryManager.isAfterCounterTrading()
                && recoveryManager.isRecovered(snapshot)) {
            fixAll(snapshot);
            return;
        }

        switch (snapshot.getStatus()) {
        case NONE:
            // ??????????????????????????????

            break;
        case ASK_SIDE:
            // ????????????????????????????????????

            if (lotManager.isLimit(snapshot)
                    && snapshot.hasOneSide()) {
                break;
            }

            if (isCalm()
                    && snapshot.getAskPipProfit() >= 0) {
                fixAsk(snapshot);
                break;
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            if (lotManager.isLimit(snapshot)
                    && snapshot.hasOneSide()) {
                break;
            }

            if (isCalm()
                    && snapshot.getBidPipProfit() >= 0) {
                fixBid(snapshot);
                break;
            }

            break;
        case SAME:
            // ?????????????????????????????????

            if (isCalm()) {
                if (snapshot.getAskPipProfit() >= 0) {
                    fixAsk(snapshot);
                    break;
                }
                if (snapshot.getBidPipProfit() >= 0) {
                    fixBid(snapshot);
                    break;
                }
                if (rateAnalyzer.isReachedAskThresholdWithin(snapshot.getRate(), Duration.ofMinutes(10))) {
                    fixBid(snapshot);
                    break;
                }
                if (rateAnalyzer.isReachedBidThresholdWithin(snapshot.getRate(), Duration.ofMinutes(10))) {
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
