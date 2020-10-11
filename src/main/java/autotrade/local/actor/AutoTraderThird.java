package autotrade.local.actor;

import java.time.Duration;
import java.util.Base64;

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
        orderDirection = OrderDirection.valueOf(Messenger.get("orderDirection"));
    }

    @Override
    protected void initialize() {
        super.initialize();
        changePair(CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderThird.order.pair")));

        Snapshot shapshot = buildSnapshot();

        if (shapshot.hasPosition()) {
            cloudLoad();
        }
    }

    @Override
    protected void cloudSave() {
        super.cloudSave();

        log.info("save snapshot when recovery start.");
        Messenger.set(
                "snapshotWhenRecoveryStart",
                Base64.getEncoder().encodeToString(AutoTradeUtils.serialize(recoveryManager.getSnapshotWhenStart())));
    }

    @Override
    protected void cloudLoad() {
        super.cloudLoad();

        log.info("load snapshot when recovery start to RecoveryManager.");
        recoveryManager.open(
                AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("snapshotWhenRecoveryStart"))));

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

            if (orderDirection == OrderDirection.ASK) {
                // 初期ポジションがAskの場合

                rateAnalyzer.updateCountertradingBid(rateAnalyzer.getRatioThresholdAsk());
                if (rateAnalyzer.getAskThreshold() > rateAnalyzer.getCountertradingAsk()) {

                    // 反対売買の閾値を更新
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getAskThreshold(),
                            rateAnalyzer.getRatioThresholdAsk());
                }
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

            if (orderDirection == OrderDirection.BID) {
                // 初期ポジションがBidの場合

                rateAnalyzer.updateCountertradingAsk(rateAnalyzer.getRatioThresholdBid());
                if (rateAnalyzer.getBidThreshold() < rateAnalyzer.getCountertradingBid()) {

                    // 反対売買の閾値を更新
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getRatioThresholdBid(),
                            rateAnalyzer.getBidThreshold());
                }
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

            switch (orderDirection) {
            case ASK:
                if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixAsk(snapshot);
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getAskThreshold(),
                            rateAnalyzer.getRatioThresholdAsk());
                }
                break;
            case BID:
                if (rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixBid(snapshot);
                    rateAnalyzer.updateCountertrading(
                            rateAnalyzer.getRatioThresholdBid(),
                            rateAnalyzer.getBidThreshold());
                }
                break;
            default:
            }

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
    protected void tradePostProcess(Snapshot snapshot) {
        super.tradePostProcess(snapshot);
        if (snapshot.hasPosition()) {
            Messenger.set("orderDirection", orderDirection.name());
        }
    }

}
