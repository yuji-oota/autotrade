package autotrade.local.actor;

import java.time.Duration;
import java.util.Base64;
import java.util.Scanner;

import autotrade.local.actor.MessageListener.ReservedMessage;
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
    protected void initialize() {
        super.initialize();
        changePair(CurrencyPair.valueOf(AutoTradeProperties.get("autoTraderFourth.order.pair")));
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

            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                // 下値閾値を超えた場合

                // 逆ポジション取得
                if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                    // スプレッドが開いている場合

                    forceSame(snapshot);
                } else {
                    // スプレッドが開いていない場合

                    if (snapshot.getAskProfit() >= 0) {
                        forceSame(snapshot);
                    } else {
                        orderBid(snapshot);
                        recoveryManager.open(snapshot);
                    }

                    if (!lotManager.isLimit(snapshot)
                            && recoveryManager.isOpen()) {
                        fixAsk(snapshot);
                    }
                }
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                // 下値閾値を超えた場合

                // 逆ポジション取得
                if (pair.getMinSpread() < snapshot.getRate().getSpread()) {
                    // スプレッドが開いている場合

                    forceSame(snapshot);
                } else {
                    // スプレッドが開いていない場合

                    if (snapshot.getBidProfit() >= 0) {
                        forceSame(snapshot);
                    } else {
                        orderAsk(snapshot);
                        recoveryManager.open(snapshot);
                    }

                    if (!lotManager.isLimit(snapshot)
                            && recoveryManager.isOpen()) {
                        fixBid(snapshot);
                    }
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
    protected boolean isFixable(Snapshot snapshot) {
        switch (snapshot.getStatus()) {
        case NONE:
        case ASK_SIDE:
        case BID_SIDE:
            break;
        case SAME:
            if (rateAnalyzer.rangeWithin(Duration.ofMinutes(5)) < 25) {
                return false;
            }
            if (snapshot.getRate().getSpread() > pair.getMinSpread()) {
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

            if (recoveryManager.isClose()) {
                // リカバリ前の場合

                if (snapshot.getAskProfit() >= 0
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    fixAsk(snapshot);
                }
            } else {
                // リカバリ後の場合

                if (recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixAll(snapshot);
                }
            }

            break;
        case BID_SIDE:
            // 売りポジションが多い場合

            if (recoveryManager.isClose()) {
                // リカバリ前の場合

                if (snapshot.getBidProfit() >= 0
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                    fixBid(snapshot);
                }
            } else {
                // リカバリ後の場合

                if (recoveryManager.isRecovered(snapshot)
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(1))) {
                    fixAll(snapshot);
                }
            }

            break;
        case SAME:
            // ポジションが同数の場合

            if (snapshot.getAskProfit() >= 0
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                fixAsk(snapshot);
                break;
            }
            if (snapshot.getBidProfit() >= 0
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                fixBid(snapshot);
                break;
            }

            if (snapshot.getAskProfit() < 0
                    && snapshot.getBidProfit() < 0) {
                if (rateAnalyzer.isReachedBidThresholdWithin(rate, Duration.ofMinutes(10))) {
                    fixAsk(snapshot);
                    break;
                }
                if (rateAnalyzer.isReachedAskThresholdWithin(rate, Duration.ofMinutes(10))) {
                    fixBid(snapshot);
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
        recoveryManager.close();
    }

    @Override
    protected MessageListener customizeMessageListener() {
        MessageListener messageListener = super.customizeMessageListener();
        messageListener.putCommand(ReservedMessage.CLOSERECOVERYMANAGER, (args) -> recoveryManager.close());
        return messageListener;
    }

}
