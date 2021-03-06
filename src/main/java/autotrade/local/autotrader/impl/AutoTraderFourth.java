package autotrade.local.autotrader.impl;

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
public class AutoTraderFourth extends AutoTrader {

    private RecoveryManager recoveryManager;

    public AutoTraderFourth() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderFourth.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderFourth.rateAnalizer.threshold.seconds")));

        System.out.print("do you need cloud load? (y or any) :");
        try (Scanner scanner = new Scanner(System.in)) {
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                cloudLoad();
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
    }

    @Override
    protected void cloudLoad() {
        super.cloudLoad();

        recoveryManager.open(
                AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("snapshotWhenRecoveryStart"))));
        log.info("loaded snapshot when recovery start to RecoveryManager {}.", recoveryManager.getSnapshotWhenStart());

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
                    // ?????????????????????????????????????????????
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
        Consumer<Snapshot> fix;

        switch (snapshot.getStatus()) {
        case NONE:
            // ??????????????????????????????

            // ?????????????????????
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(snapshot);
                recoveryManager.open(snapshot);
                rateAnalyzer.setCountertradingBid(rateAnalyzer.getRatioThresholdAsk());
                return;
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
                recoveryManager.open(snapshot);
                rateAnalyzer.setCountertradingAsk(rateAnalyzer.getRatioThresholdBid());
                return;
            }

            break;
        case ASK_SIDE:
            // ????????????????????????????????????

            isReachedThreshold = r -> rateAnalyzer.isReachedBidThreshold(r);
            counterTrading = s -> forceSame(s);
            fix = s -> fixAsk(s);
            if (snapshot.getAskProfit() < 0) {
                counterTrading = s -> orderBid(s);
                if (lotManager.isLimit(snapshot)) {
                    counterTrading = s -> {};
                    fix = s -> fixAll(s);
                }
            }
            if (!lotManager.isLimit(snapshot)
                    && pair.getMinSpread() < snapshot.getRate().getSpread()) {
                // ???????????????????????????????????????
                counterTrading = s -> forceSame(s);
                fix = s -> {};
            }

            // ?????????????????????
            if (rateAnalyzer.isReachedCountertradingBid(rate)) {
                forceSame(snapshot);
                break;
            }

            if (recoveryManager.isReachedRecover()
                    && !recoveryManager.isRecovered(snapshot)) {
                forceSame(snapshot);
                break;
            }

            if (isReachedThreshold.test(rate)) {
                // ??????????????????????????????

                counterTrading.accept(snapshot);
                fix.accept(snapshot);
                rateAnalyzer.setCountertradingAsk(rateAnalyzer.getRatioThresholdBid());
                break;
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            isReachedThreshold = r -> rateAnalyzer.isReachedAskThreshold(r);
            counterTrading = s -> forceSame(s);
            fix = s -> fixBid(s);
            if (snapshot.getBidProfit() < 0) {
                counterTrading = s -> orderAsk(s);
                if (lotManager.isLimit(snapshot)) {
                    counterTrading = s -> {};
                    fix = s -> fixAll(s);
                }
            }
            if (!lotManager.isLimit(snapshot)
                    && pair.getMinSpread() < snapshot.getRate().getSpread()) {
                // ???????????????????????????????????????
                counterTrading = s -> forceSame(s);
                fix = s -> {};
            }

            // ?????????????????????
            if (rateAnalyzer.isReachedCountertradingAsk(rate)) {
                forceSame(snapshot);
                break;
            }

            if (recoveryManager.isReachedRecover()
                    && !recoveryManager.isRecovered(snapshot)) {
                forceSame(snapshot);
                break;
            }

            if (isReachedThreshold.test(rate)) {
                // ??????????????????????????????

                counterTrading.accept(snapshot);
                fix.accept(snapshot);
                rateAnalyzer.setCountertradingBid(rateAnalyzer.getRatioThresholdAsk());
                break;
            }

            break;
        case SAME:
            // ?????????????????????????????????

            break;
        default:
        }

    }

    @Override
    protected boolean isFixable(Snapshot snapshot) {
        switch (snapshot.getStatus()) {
        case NONE:
        case ASK_SIDE:
        case BID_SIDE:
            break;
        case SAME:
            if (isCalm()) {
                return false;
            }
            if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
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

            if (recoveryManager.isClose()) {
                // ????????????????????????

                if (snapshot.getAskProfit() >= 0
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    fixAsk(snapshot);
                }
            } else {
                // ????????????????????????

                if (recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isBidDown()) {
                    fixAll(snapshot);
                }
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            if (recoveryManager.isClose()) {
                // ????????????????????????

                if (snapshot.getBidProfit() >= 0
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                    fixBid(snapshot);
                }
            } else {
                // ????????????????????????

                if (recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isAskUp()) {
                    fixAll(snapshot);
                }
            }

            break;
        case SAME:
            // ?????????????????????????????????

            if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(10))) {
                fixAsk(snapshot);
                rateAnalyzer.resetCountertrading();
                recoveryManager.resetReachedRecover();
                break;
            }
            if (rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(10))) {
                fixBid(snapshot);
                rateAnalyzer.resetCountertrading();
                recoveryManager.resetReachedRecover();
                break;
            }

            break;
        default:
        }
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        recoveryManager.close();
    }

    @Override
    protected MessageListener customizeMessageListener() {
        MessageListener messageListener = super.customizeMessageListener();
        messageListener.putCommand(ReservedMessage.CLOSERECOVERYMANAGER, (args) -> recoveryManager.close());
        return messageListener;
    }

}
