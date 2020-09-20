package autotrade.local.actor;

import java.time.Duration;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;

public class AutoTraderSecond extends AutoTrader {

    public AutoTraderSecond() {
        super();
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
                    orderBid(snapshot);
                } else {
                    forceSame();
                }
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            // 閾値超過を判定
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                if (snapshot.getAskLot() == 0) {
                    orderAsk(snapshot);
                } else {
                    forceSame();
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
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
            // breakなし
        case BID_SIDE:
            // 売りポジションが多い場合
            // breakなし
        case SAME:
            // ポジションが同数の場合

            if (snapshot.getAskProfit() >= 0
                && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                fixAsk(snapshot);
            }
            if (snapshot.getBidProfit() >= 0
                && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                fixBid(snapshot);
            }

            break;
        default:
        }
    }

}
