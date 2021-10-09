package autotrade.local.actor;

import java.time.Duration;
import java.util.Base64;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Predicate;

import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderSixth extends AutoTrader {

    private RecoveryManager recoveryManager;
    private enum OrderPoint {
        THRESHOLD, AVERAGE;
        private static OrderPoint prev = THRESHOLD;
        public OrderPoint next() {
            if (prev == THRESHOLD
                    && prev == this) {
                prev = this;
                return AVERAGE;
            }
            prev = this;
            return THRESHOLD;
        }
        public OrderPoint prev() {
            return prev;
        }
    }
    private OrderPoint nextOrderPoint;

    public AutoTraderSixth() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderSixth.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderSixth.rateAnalizer.threshold.seconds")));

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
            case ASK_SIDE:
            case BID_SIDE:
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
            // ポジションがない場合

            if (rateAnalyzer.isAskUp()) {
                if (rateAnalyzer.isReachedAskThreshold(rate)
                        || rateAnalyzer.isReachedAverageAsk(rate)) {
                    orderAsk(snapshot);
                    recoveryManager.open(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    nextOrderPoint = OrderPoint.AVERAGE;
                    return;
                }
            }

            if (rateAnalyzer.isBidDown()) {
                if (rateAnalyzer.isReachedBidThreshold(rate)
                        || rateAnalyzer.isReachedAverageBid(rate)) {
                    orderBid(snapshot);
                    recoveryManager.open(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    nextOrderPoint = OrderPoint.AVERAGE;
                    return;
                }
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合

            if (rateAnalyzer.isBidDown()) {

                isReachedThreshold = r -> rateAnalyzer.isReachedBidThreshold(r);
                counterTrading = s -> orderBid(s);
                fix = s -> fixAsk(s);
                if (nextOrderPoint == OrderPoint.AVERAGE) {
                    isReachedThreshold = r -> rateAnalyzer.isReachedAverageBid(r);
                }
                if (recoveryManager.isSuccessCounterTradingAsk(rate)) {
                    counterTrading = s -> forceSame(s);
                    fix = s -> fixAsk(s);
                }
                if (snapshot.isSpreadWiden()) {
                    counterTrading = s -> forceSame(s);
                    fix = s -> {};
                }
                if (lotManager.isLimit(snapshot)) {
                    counterTrading = s -> {};
                    fix = s -> fixAll(s);
                }

                if (isReachedThreshold.test(rate)) {
                    counterTrading.accept(snapshot);
                    fix.accept(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    nextOrderPoint = nextOrderPoint.next();
                    return;
                }
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            if (rateAnalyzer.isAskUp()) {
                isReachedThreshold = r -> rateAnalyzer.isReachedAskThreshold(r);
                counterTrading = s -> orderAsk(s);
                fix = s -> fixBid(s);
                if (nextOrderPoint == OrderPoint.AVERAGE) {
                    isReachedThreshold = r -> rateAnalyzer.isReachedAverageAsk(r);
                }
                if (recoveryManager.isSuccessCounterTradingBid(rate)) {
                    counterTrading = s -> forceSame(s);
                    fix = s -> fixBid(s);
                }
                if (snapshot.isSpreadWiden()) {
                    counterTrading = s -> forceSame(s);
                    fix = s -> {};
                }
                if (lotManager.isLimit(snapshot)) {
                    counterTrading = s -> {};
                    fix = s -> fixAll(s);
                }

                if (isReachedThreshold.test(rate)) {
                    counterTrading.accept(snapshot);
                    fix.accept(snapshot);
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    nextOrderPoint = nextOrderPoint.next();
                    return;
                }
            }

            break;
        case SAME:
            // ポジションが同数の場合

            break;
        default:
        }

    }

    @Override
    protected boolean isCalm() {
        return rateAnalyzer.rangeWithin(Duration.ofMinutes(5)) < 50;
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
                && !lotManager.isInitial(snapshot)
                && recoveryManager.isRecovered(snapshot)) {
            fixAll(snapshot);
            return;
        }

        Rate rate = snapshot.getRate();
        Predicate<Rate> isReachedThreshold;
        Consumer<Snapshot> fix;

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合

            if (recoveryManager.isRecovered(snapshot)
                    && rateAnalyzer.isBidDown()
                    && snapshot.getAskPipProfit() >= 5) {
                fixAll(snapshot);
                break;
            }
            if (rateAnalyzer.isBidDown()
                    && recoveryManager.getRecoveryProgress(snapshot) >= 75) {
                fixAll(snapshot);
                break;
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            if (recoveryManager.isRecovered(snapshot)
                    && rateAnalyzer.isAskUp()
                    && snapshot.getBidPipProfit() >= 5) {
                fixAll(snapshot);
                break;
            }
            if (rateAnalyzer.isAskUp()
                    && recoveryManager.getRecoveryProgress(snapshot) >= 75) {
                fixAll(snapshot);
                break;
            }

            break;
        case SAME:
            // ポジションが同数の場合
            isReachedThreshold = r -> false;
            fix = s -> {};
            if (rateAnalyzer.isBidDown()) {
                isReachedThreshold = r -> rateAnalyzer.isReachedBidThreshold(r);
                fix = s -> fixAsk(s);
            }
            if (rateAnalyzer.isAskUp()) {
                isReachedThreshold = r -> rateAnalyzer.isReachedAskThreshold(r);
                fix = s -> fixBid(s);
            }

            if (isReachedThreshold.test(rate)) {
                fix.accept(snapshot);
                recoveryManager.setCounterTradingSnapshot(snapshot);
                recoveryManager.resetReachedRecover();
                nextOrderPoint = OrderPoint.AVERAGE;
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
