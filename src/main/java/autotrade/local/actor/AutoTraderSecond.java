package autotrade.local.actor;

import java.time.Duration;

import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;

public class AutoTraderSecond extends AutoTrader {

    private RecoveryManager recoveryManager;

    public AutoTraderSecond() {
        super();
        recoveryManager = new RecoveryManager();
    }

    @Override
    protected void initialize() {
        super.initialize();
        changePair(CurrencyPair.valueOf(AutoTradeProperties.get("autotraderSecond.order.pair")));
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            // RecoveryManager初期化
            recoveryManager.done();

            // 閾値超過を判定
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(snapshot);
                return;
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
                return;
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合

            // 閾値超過を判定
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                if (snapshot.getBidLot() == 0) {
                    recoveryManager.start(snapshot);
                    orderBid(snapshot);
                } else {
                    forceSame();
                }
            }

            // リカバリ判定
            if (recoveryManager.isCutOffBid()
                    && recoveryManager.isSuccessCutOffBid(snapshot)
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                forceSame();
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            // 閾値超過を判定
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                if (snapshot.getAskLot() == 0) {
                    recoveryManager.start(snapshot);
                    orderAsk(snapshot);
                } else {
                    forceSame();
                }
            }

            // リカバリ判定
            if (recoveryManager.isCutOffAsk()
                    && recoveryManager.isSuccessCutOffAsk(snapshot)
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                forceSame();
            }

            break;
        case SAME:
            // ポジションが同数の場合

            // RecoveryManager初期化
            recoveryManager.cutOffDone();

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
            } else {
                // リカバリ中の場合

                if (snapshot.getAskProfit() >= 0
                            && snapshot.hasBothSide()
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                    recoveryManager.cutOffAsk(snapshot);
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
            } else {
                // リカバリ中の場合

                if (snapshot.getBidProfit() >= 0
                        && snapshot.hasBothSide()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                    recoveryManager.cutOffBid(snapshot);
                    fixBid(snapshot);
                }
            }

            break;
        case SAME:
            // ポジションが同数の場合

            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                recoveryManager.cutOffAsk(snapshot);
                fixAsk(snapshot);
                break;
            }
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                recoveryManager.cutOffBid(snapshot);
                fixBid(snapshot);
                break;
            }

            break;
        default:
        }
    }

}
