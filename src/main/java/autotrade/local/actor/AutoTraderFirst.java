package autotrade.local.actor;

import java.time.Duration;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTraderFirst extends AutoTrader {

    public AutoTraderFirst() {
        super();
    }

    @Override
    protected void fix(Snapshot snapshot) {

        switch (snapshot.getStatus()) {
        case NONE:
            SameManager.close();
            break;
        case SAME:
            if (reserveManager.isResevedLimitFixAsk()
                    && reserveManager.isLimitFixAsk(snapshot.getRate())) {
                fixAsk(snapshot);
                reserveManager.resetReserve();
            }
            if (reserveManager.isResevedLimitFixBid()
                    && reserveManager.isLimitFixBid(snapshot.getRate())) {
                fixBid(snapshot);
                reserveManager.resetReserve();
            }
            if (reserveManager.isResevedStopFixAsk()
                    && reserveManager.isStopFixAsk(snapshot.getRate())) {
                fixAsk(snapshot);
                reserveManager.resetReserve();
            }
            if (reserveManager.isResevedStopFixBid()
                    && reserveManager.isStopFixBid(snapshot.getRate())) {
                fixBid(snapshot);
                reserveManager.resetReserve();
            }
            break;
        case ASK_SIDE:
        case BID_SIDE:

            // Sameポジション発生後の利益確定判定
            if (SameManager.hasInstance()) {

                // Sameポジション回復中の場合
                if (SameManager.getInstance().isRecovered(snapshot)) {
                    // Sameポジション回復達成で利益確定
                    fixAll(snapshot);
                    // 注文再開
                    changeThroughOrder(false);
                    changeAutoRecommended(true);
                }
                return;
            }

//            if (rateAnalyzer.isSenceOfDirection()) {
//                // 値動きに方向感がある場合
//                if (isFix(snapshot, lotManager.getInitial() * 0)) {
//                    // 目標金額達成で利益確定
//                    log.info("achieved target amount.");
//                    fixAll(snapshot);
//                    return;
//                }
//            } else {
//                // 値動きに方向感がない場合
//                if (snapshot.getPositionProfit() >= lotManager.getInitial() * 5) {
//                    // 目標金額達成で利益確定
//                    log.info("achieved target amount.");
//                    fixAll(snapshot);
//                    return;
//                }
//            }
//            if (snapshot.hasBothSide()
//                    && snapshot.getPositionProfit() >= 0) {
//                // 反対売買の場合
//                // 目標金額達成で利益確定
//                log.info("achieved countertrading.");
//                fixAll(snapshot);
//                return;
//            }

            // 利益0以上1分足逆行で利益確定
            if (isFix(snapshot, lotManager.getInitial() * 0)) {
                // 目標金額達成で利益確定
                log.info("achieved target amount.");
                fixAll(snapshot);
                return;
            }
            break;
        default:
        }
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
                rateAnalyzer.saveCountertradingThreshold(
                        rateAnalyzer.getAskThreshold(),
                        rateAnalyzer.getRatioThresholdAsk());
                rateAnalyzer.saveIsSenceOfDirection();
                return;
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
                rateAnalyzer.saveCountertradingThreshold(
                        rateAnalyzer.getRatioThresholdBid(),
                        rateAnalyzer.getBidThreshold());
                rateAnalyzer.saveIsSenceOfDirection();
                return;
            }

            // 急反転、且つ１分足の閾値超過を判定
            if (rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))
                    && rateAnalyzer.passCountWithin(rate.getAsk(), 5) == 2) {
                orderAsk(snapshot);
                rateAnalyzer.saveCountertradingThreshold(
                        rate.getAsk(),
                        rate.getAsk() - (rateAnalyzer.getAskThreshold() - rateAnalyzer.getRatioThresholdAsk()));
                rateAnalyzer.saveIsSenceOfDirection();
                return;
            }
            if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))
                    && rateAnalyzer.passCountWithin(rate.getBid(), 5) == 2) {
                orderBid(snapshot);
                rateAnalyzer.saveCountertradingThreshold(
                        rate.getBid() + (rateAnalyzer.getRatioThresholdBid() - rateAnalyzer.getBidThreshold()),
                        rate.getBid());
                rateAnalyzer.saveIsSenceOfDirection();
                return;
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
            if (!SameManager.hasInstance()
                    && rateAnalyzer.isReachedCountertradingBid(rate)) {
                // 下値閾値を超えた場合
                // 逆ポジション取得
                orderBid(snapshot);
            }
            break;
        case BID_SIDE:
            // 売りポジションが多い場合
            if (!SameManager.hasInstance()
                    && rateAnalyzer.isReachedCountertradingAsk(rate)) {
                // 上値閾値を超えた場合
                // 逆ポジション取得
                orderAsk(snapshot);
            }
            break;
        case SAME:
            // ポジションが同数の場合
            break;
        default:
        }

    }

    @Override
    protected void tradePostProcess(Snapshot snapshot) {

        // SameManager初期化
        if (snapshot.isPositionSame()) {
            if (!SameManager.hasInstance()) {
                changeThroughOrder(true);
                changeAutoRecommended(false);
            }
            SameManager.setSnapshot(snapshot);
        }

        super.tradePostProcess(snapshot);
    }

    private boolean isFix(Snapshot snapshot, int targetAmount) {
        if (snapshot.getPositionProfit() >= targetAmount) {
            if (snapshot.isPositionAskSide()) {
                return rateAnalyzer.isReachedBidThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
            }
            if (snapshot.isPositionBidSide()) {
                return rateAnalyzer.isReachedAskThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
            }
        }
        return false;
    }

}
