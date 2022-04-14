package autotrade.local.autotrader.impl;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import autotrade.local.actor.RecoveryManager;
import autotrade.local.autotrader.AbstractAutoTrader;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Pair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * ナンピンロジック　＋　両建て
 */
@Component("autoTrader30th")
@Slf4j
public class AutoTrader30th extends AbstractAutoTrader {

    private boolean doAsk;
    private boolean doBid;

    @Autowired
    private RecoveryManager recoveryManager;

    @Autowired
    private ToIntFunction<Snapshot> toMinimumProfit;

    @Autowired
    private ToIntFunction<Snapshot> toInitialLot;

    @Autowired
    private ToIntFunction<Snapshot> toNextLot;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.order.duration.seconds}')}")
    private Duration orderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.followUpOrder.duration.seconds}')}")
    private Duration followUpOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.doOrder.duration.seconds}')}")
    private Duration doOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.fix.duration.seconds}')}")
    private Duration fixDuration;

    public AutoTrader30th() {
        super();
        log.info("autoTrader30th started.");
    }

    @Override
    protected void saveLocal() {
        super.saveLocal();
        AutoTradeUtils.localSave(Paths.get("localSave", "recoveryManager"), recoveryManager);
    }

    @Override
    protected void loadLocal() {
        super.loadLocal();
        recoveryManager = AutoTradeUtils.localLoad(Paths.get("localSave", "recoveryManager"));
        log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
    }

    @Override
    protected Pair selectPair() {

        if (recoveryManager.isOpen()) {
            return recoveryManager.getHandlePair();
        }

        changeDisplay(DisplayMode.RATELIST);
        // 推奨通貨ペア選択
        LocalTime now = LocalTime.now();
        return pairManager.getPairs().stream()
                .filter(pair -> pair.isHandleable(now))
                .filter(pair -> buildRateFromList(pair).isSpreadNarrow())
                .map(pair -> {
                    return new SimpleEntry<Pair, Integer>(
                            pair,
                            pairAnalyzerMap.get(pair.getName()).rangeWithin(orderDuration) - pair.getMinSpread());
                })
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(SimpleEntry::getKey)
                .orElseGet(pairManager::getDefault);
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.hasNoPosition();
    }

    protected boolean isTradable(Snapshot snapshot) {
        if (!super.isTradable(snapshot)) {
            return false;
        }
        if (recoveryManager.isOpen()
                && !recoveryManager.getHandlePair().equals(snapshot.getPair())) {
            return false;
        }
        return true;
    }

    @Override
    protected void preTrade(Snapshot snapshot) {
        if (recoveryManager.isOpen()
                && isSleep(snapshot)) {
            saveLocal();
        }

        super.preTrade(snapshot);
    }

    @Override
    protected boolean fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        if (recoveryManager.isRecoveredWithProfit(snapshot, toMinimumProfit)) {
            if (rateAnalyzer.isBidDown()
                    && snapshot.isBidLtAsk()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, fixDuration)) {
                fixAll(snapshot);
                return true;
            }
            if (rateAnalyzer.isAskUp()
                    && snapshot.isBidGtAsk()
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, fixDuration)) {
                fixAll(snapshot);
                return true;
            }
        }
        
        // 反対売買用
        if (rateAnalyzer.isBidDown()
                && snapshot.getAskProfit() > 0
                && snapshot.isBidGtAsk()
                && rateAnalyzer.isReachedBidThresholdWithin(rate, fixDuration)) {
            fixAsk(snapshot);
            return true;
        }
        if (rateAnalyzer.isAskUp()
                && snapshot.getBidProfit() > 0
                && snapshot.isBidLtAsk()
                && rateAnalyzer.isReachedAskThresholdWithin(rate, fixDuration)) {
            fixBid(snapshot);
            return true;
        }
        
        return false;
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        if (!super.isOrderable(snapshot)) {
            return false;
        }
        if (rateAnalyzer.getRates().size() == rateAnalyzer.ratesWithin(orderDuration).size()) {
            return false;
        }
        return true;
    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();

        switch (snapshot.getStatus()) {
        case NO_POSITION:
            // ポジションがない場合

            if (recoveryManager.isClose()) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, orderDuration)) {
                    recoveryManager.open(snapshot);
                    orderAsk(toInitialLot.applyAsInt(snapshot), snapshot);
                    printRecoveryProgress(snapshot);
                    doAsk = false;
                }
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDuration)) {
                    recoveryManager.open(snapshot);
                    orderBid(toInitialLot.applyAsInt(snapshot), snapshot);
                    printRecoveryProgress(snapshot);
                    doBid = false;
                }
                return;
            }

            break;
        case BID_LT_ASK:
            // 買いポジションが多い場合
        case BID_GT_ASK:
            // 売りポジションが多い場合
        case BID_EQ_ASK:
            // ポジションが同数の場合

            Duration duration = followUpOrderDuration;
            if (snapshot.getMoreLot() < toInitialLot.applyAsInt(recoveryManager.getOpenSnapshot()) * 2) {
                duration = Duration.ofSeconds(150);
            }

            if (!snapshot.isSpreadWiden()) {
                if (rateAnalyzer.isAskUp()) {
                    if (doAsk
                            && snapshot.isAskLtLimit()
                            && snapshot.isBidLtAsk()
                            && rateAnalyzer.isReachedAskThresholdWithin(rate, duration)) {
                        orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
                        doAsk = false;
                        return;
                    }
                }
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && snapshot.isBidGtAsk()
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, duration)) {
                        orderBid(toNextLot.applyAsInt(snapshot), snapshot);
                        doBid = false;
                        return;
                    }
                }
            }
            
            // 反対売買用
            if (snapshot.getMoreLot() >= 2) {
                if (snapshot.getAskLot() == 0) {
                    doAsk = true;
                }
                if (rateAnalyzer.isAskUp()) {
                    if (doAsk
                            && snapshot.getBidLot() / 2 >= snapshot.getAskLot()
                            && snapshot.isBidGtAsk()
                            && rateAnalyzer.isReachedAskThresholdWithin(rate, followUpOrderDuration)) {
                        orderAsk(1, snapshot);
                        doAsk = false;
                        return;
                    }
                }
                if (snapshot.getBidLot() == 0) {
                    doBid = true;
                }
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.getBidLot() <= snapshot.getAskLot() / 2
                            && snapshot.isBidLtAsk()
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, followUpOrderDuration)) {
                        orderBid(1, snapshot);
                        doBid = false;
                        return;
                    }
                }
            }

            break;
        default:
        }

    }

    @Override
    protected void postOrder(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        if (rateAnalyzer.isAskUp()
                && rateAnalyzer.isReachedAskThresholdWithin(rate, doOrderDuration)) {
            doBid = true;
        }
        if (rateAnalyzer.isBidDown()
                && rateAnalyzer.isReachedBidThresholdWithin(rate, doOrderDuration)) {
            doAsk = true;
        }
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        recoveryManager.printSummary(snapshot);
        recoveryManager.close();
    }

    private void printRecoveryProgress(Snapshot snapshot) {
        log.info("recovery progress:{} profit:{}",
                recoveryManager.getRecoveryProgress(snapshot),
                recoveryManager.getProfit(snapshot));
    }

    @Override
    protected boolean isCalm() {
        return false;
    }

}
