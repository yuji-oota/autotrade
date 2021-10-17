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
public class AutoTraderFifth extends AutoTrader {

    private RecoveryManager recoveryManager;

    public AutoTraderFifth() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.get(
                CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderFifth.order.pair"))).setThresholdDuration(
                        Duration.ofSeconds(
                                AutoTradeProperties.getInt("autoTraderFifth.rateAnalizer.threshold.seconds")));

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

    private Duration getDurationByRecoveryProgress(Snapshot snapshot) {
        int progress = recoveryManager.getRecoveryProgress(snapshot);
        if (progress <= 0) {
            return Duration.ofSeconds(600);
        }
        if (progress >= 100) {
            return Duration.ofSeconds(600);
        }
        return Duration.ofSeconds(600 * (100 - progress) / 100);
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

            // 閾値超過を判定
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(snapshot);
                recoveryManager.open(snapshot);
                recoveryManager.setCounterTradingSnapshot(snapshot);
                return;
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
                recoveryManager.open(snapshot);
                recoveryManager.setCounterTradingSnapshot(snapshot);
                return;
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合

            if (recoveryManager.isReachedRecover()
                    && !recoveryManager.isRecovered(snapshot)) {
                forceSame(snapshot);
                break;
            }

            if (rateAnalyzer.isReachedBidThresholdWithin(rate, getDurationByRecoveryProgress(snapshot))) {

                if (isCalm()) {
                    // 値動きがない場合
                    forceSame(snapshot);
                    break;
                }

                if (!recoveryManager.isSuccessCounterTradingAsk(rate)) {
                    // 反対売買失敗中の場合
                    if (lotManager.isLimit(snapshot)) {
                        fixAll(snapshot);
                    } else {
                        if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                            // スプレッドが開いている場合
                            forceSame(snapshot);
                        } else {
                            orderBid(snapshot);
                            fixAsk(snapshot);
                        }
                    }
                } else {
                    // 反対売買成功中の場合

                    forceSame(snapshot);
                    if (pair.getMinSpread() >= snapshot.getRate().getSpread()) {
                        // スプレッドが開いていない場合

                        fixAsk(snapshot);
                    }
                }
                recoveryManager.setCounterTradingSnapshot(snapshot);
                break;
            }
            if (!snapshot.hasBothSide()
                    && !recoveryManager.isSuccessCounterTradingAsk(rate)
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot.getAskLot() / 2);
                break;
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            if (recoveryManager.isReachedRecover()
                    && !recoveryManager.isRecovered(snapshot)) {
                forceSame(snapshot);
                break;
            }

            if (rateAnalyzer.isReachedAskThresholdWithin(rate, getDurationByRecoveryProgress(snapshot))) {

                if (isCalm()) {
                    // 値動きがない場合
                    forceSame(snapshot);
                    break;
                }

                if (!recoveryManager.isSuccessCounterTradingBid(rate)) {
                    // 反対売買失敗中の場合
                    if (lotManager.isLimit(snapshot)) {
                        fixAll(snapshot);
                    } else {
                        if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                            // スプレッドが開いている場合
                            forceSame(snapshot);
                        } else {
                            orderAsk(snapshot);
                            fixBid(snapshot);
                        }
                    }
                } else {
                    // 反対売買成功中の場合

                    forceSame(snapshot);
                    if (pair.getMinSpread() >= snapshot.getRate().getSpread()) {
                        // スプレッドが開いていない場合

                        fixBid(snapshot);
                    }
                }
                recoveryManager.setCounterTradingSnapshot(snapshot);
                break;
            }
            if (!snapshot.hasBothSide()
                    && !recoveryManager.isSuccessCounterTradingBid(rate)
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(snapshot.getBidLot() / 2);
                break;
            }

            break;
        case SAME:
            // ポジションが同数の場合

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
            // ポジションがない場合

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合

            if (recoveryManager.isRecovered(snapshot)
                    && rateAnalyzer.isBidDown()) {
                fixAll(snapshot);
                break;
            }
            if (snapshot.hasBothSide()
                    && snapshot.getBidProfit() >= 0
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                fixBid(snapshot);
                break;
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            if (recoveryManager.isRecovered(snapshot)
                    && rateAnalyzer.isAskUp()) {
                fixAll(snapshot);
                break;
            }
            if (snapshot.hasBothSide()
                    && snapshot.getAskProfit() >= 0
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                fixAsk(snapshot);
                break;
            }

            break;
        case SAME:
            // ポジションが同数の場合

            if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(10))) {
                recoveryManager.setCounterTradingSnapshot(snapshot);
                fixAsk(snapshot);
                recoveryManager.resetReachedRecover();
                break;
            }
            if (rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(10))) {
                recoveryManager.setCounterTradingSnapshot(snapshot);
                fixBid(snapshot);
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
