package autotrade.local.autotrader.impl;

import java.time.Duration;
import java.util.Base64;
import java.util.Scanner;

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
public class AutoTraderThird extends AutoTrader {

    private RecoveryManager recoveryManager;

    private enum OrderDirection {ASK, BID}
    private OrderDirection orderDirection;

    public AutoTraderThird() {
        super();
        recoveryManager = new RecoveryManager();

        System.out.print("do you need cloud load? (y or any) :");
        try (Scanner scanner = new Scanner(System.in)) {
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                cloudLoad();
            }
        };
    }

    @Override
    protected void initialize() {
        super.initialize();
        changePair(CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderThird.order.pair")));
    }

    @Override
    protected void cloudSave() {
        super.cloudSave();

        Messenger.set("orderDirection", orderDirection.name());
        log.info("saved order direction {}.", orderDirection.name());

        Messenger.set(
                "snapshotWhenRecoveryStart",
                Base64.getEncoder().encodeToString(AutoTradeUtils.serialize(recoveryManager.getSnapshotWhenStart())));
        log.info("saved snapshot when recovery start {}.", recoveryManager.getSnapshotWhenStart());
    }

    @Override
    protected void cloudLoad() {
        super.cloudLoad();

        orderDirection = OrderDirection.valueOf(Messenger.get("orderDirection"));
        log.info("loaded order direction {}.", orderDirection);

        recoveryManager.open(
                AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("snapshotWhenRecoveryStart"))));
        log.info("loaded snapshot when recovery start to RecoveryManager {}.", recoveryManager.getSnapshotWhenStart());

    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NONE:
            // ??????????????????????????????

            // ?????????????????????
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(snapshot);
                rateAnalyzer.updateCountertrading(
                        rateAnalyzer.getAskThreshold(),
                        rateAnalyzer.getRatioThresholdAsk());
                orderDirection = OrderDirection.ASK;
                return;
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
                rateAnalyzer.updateCountertrading(
                        rateAnalyzer.getRatioThresholdBid(),
                        rateAnalyzer.getBidThreshold());
                orderDirection = OrderDirection.BID;
                return;
            }

            break;
        case ASK_SIDE:
            // ????????????????????????????????????

            switch (orderDirection) {
            case ASK:

                rateAnalyzer.setCountertradingBid(rateAnalyzer.getRatioThresholdAsk());
                if (rateAnalyzer.getAskThreshold() > rateAnalyzer.getCountertradingAsk()) {

                    // ??????????????????????????????
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getAskThreshold(),
                            rateAnalyzer.getRatioThresholdAsk());
                }
                break;
            case BID:

                if (lotManager.isLimit(snapshot)
                && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(5))) {
                    forceSame(snapshot);
                    return;
                }
                break;
            default:
            }

            if (rateAnalyzer.isReachedCountertradingBid(rate)) {
                // ??????????????????????????????

                // ????????????????????????
                if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                    forceSame(snapshot);
                } else {
                    orderBid(snapshot);
                    if (!lotManager.isLimit(snapshot)) {
                        fixAsk(snapshot);
                    }
                }
                recoveryManager.open(snapshot);
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            switch (orderDirection) {
            case ASK:

                if (lotManager.isLimit(snapshot)
                && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(5))) {
                    forceSame(snapshot);
                    return;
                }
                break;
            case BID:

                rateAnalyzer.setCountertradingAsk(rateAnalyzer.getRatioThresholdBid());
                if (rateAnalyzer.getBidThreshold() < rateAnalyzer.getCountertradingBid()) {

                    // ??????????????????????????????
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getRatioThresholdBid(),
                            rateAnalyzer.getBidThreshold());
                }
                break;
            default:
            }

            if (rateAnalyzer.isReachedCountertradingAsk(rate)) {
                // ??????????????????????????????

                // ????????????????????????
                if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                    forceSame(snapshot);
                } else {
                    orderAsk(snapshot);
                    if (!lotManager.isLimit(snapshot)) {
                        fixBid(snapshot);
                    }
                }
                recoveryManager.open(snapshot);
            }

            break;
        case SAME:
            // ?????????????????????????????????

            break;
        default:
        }

    }

    @Override
    protected void fix(Snapshot snapshot) {

        // ??????????????????
        if (snapshot.hasPosition()
                && recoveryManager.isOpen()
                && recoveryManager.isRecovered(snapshot)) {
            fixAll(snapshot);
            return;
        }

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
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixAsk(snapshot);
                }
            }

            break;
        case BID_SIDE:
            // ????????????????????????????????????

            if (recoveryManager.isClose()) {
                // ????????????????????????


                if (snapshot.getBidProfit() >= 0
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixBid(snapshot);
                }
            }

            break;
        case SAME:
            // ?????????????????????????????????

            if (snapshot.getAskProfit() >= 0
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                fixAsk(snapshot);
                rateAnalyzer.updateCountertrading(
                        rateAnalyzer.getAskThreshold(),
                        rateAnalyzer.getRatioThresholdAsk());
                orderDirection = OrderDirection.ASK;
                break;
            }
            if (snapshot.getBidProfit() >= 0
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                fixBid(snapshot);
                rateAnalyzer.updateCountertrading(
                        rateAnalyzer.getRatioThresholdBid(),
                        rateAnalyzer.getBidThreshold());
                orderDirection = OrderDirection.BID;
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

}
