package autotrade.local.actor;

import java.time.Duration;
import java.util.Base64;
import java.util.Scanner;

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
;
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
            // ポジションがない場合

            // 閾値超過を判定
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
            // 買いポジションが多い場合

            switch (orderDirection) {
            case ASK:

                rateAnalyzer.updateCountertradingBid(rateAnalyzer.getRatioThresholdAsk());
                if (rateAnalyzer.getAskThreshold() > rateAnalyzer.getCountertradingAsk()) {

                    // 反対売買の閾値を更新
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getAskThreshold(),
                            rateAnalyzer.getRatioThresholdAsk());
                }
                break;
            case BID:

                if (snapshot.getAskProfit() >= 0
                && lotManager.isLimit(snapshot)
                && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(5))) {
                    forceSame();
                }

                break;
            default:
            }

            if (rateAnalyzer.isReachedCountertradingBid(rate)) {
                // 下値閾値を超えた場合

                // 逆ポジション取得
                if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                    forceSame();
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
            // 売りポジションが多い場合

            switch (orderDirection) {
            case ASK:

                if (snapshot.getBidProfit() >= 0
                && lotManager.isLimit(snapshot)
                && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(5))) {
                    forceSame();
                }
                break;
            case BID:

                rateAnalyzer.updateCountertradingAsk(rateAnalyzer.getRatioThresholdBid());
                if (rateAnalyzer.getBidThreshold() < rateAnalyzer.getCountertradingBid()) {

                    // 反対売買の閾値を更新
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getRatioThresholdBid(),
                            rateAnalyzer.getBidThreshold());
                }
                break;
            default:
            }

            if (rateAnalyzer.isReachedCountertradingAsk(rate)) {
                // 上値閾値を超えた場合

                // 逆ポジション取得
                if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                    forceSame();
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
            // ポジションが同数の場合

            break;
        default:
        }

    }

    @Override
    protected void fix(Snapshot snapshot) {

        // リカバリ判定
        if (snapshot.hasPosition()
                && recoveryManager.isOpen()
                && recoveryManager.isRecovered(snapshot)) {
            fixAll(snapshot);
            return;
        }

        Rate rate = snapshot.getRate();
        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合

            if (recoveryManager.isClose()) {
                // リカバリ前の場合

                if (snapshot.getAskProfit() >= 0
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixAsk(snapshot);
                }
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            if (recoveryManager.isClose()) {
                // リカバリ前の場合


                if (snapshot.getBidProfit() >= 0
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixBid(snapshot);
                }
            }

            break;
        case SAME:
            // ポジションが同数の場合

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
