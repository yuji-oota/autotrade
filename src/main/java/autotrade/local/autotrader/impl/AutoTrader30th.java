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

import autotrade.local.actor.RangeManager;
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
    private RangeManager rangeManagerMain;

    @Autowired
    private RangeManager rangeManagerSub;

    @Autowired
    private ToIntFunction<Snapshot> toMinimumProfit;

    @Autowired
    private ToIntFunction<Snapshot> toInitialLot;

    @Autowired
    private ToIntFunction<Snapshot> toNextLot;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.order.duration.seconds}')}")
    private Duration orderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.counterOrder.duration.seconds}')}")
    private Duration counterOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.followUpOrder.duration.seconds.short}')}")
    private Duration followUpOrderDurationShort;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.followUpOrder.duration.seconds.long}')}")
    private Duration followUpOrderDurationLong;

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
        AutoTradeUtils.localSave(Paths.get("localSave", "rangeManagerMain"), rangeManagerMain);
        AutoTradeUtils.localSave(Paths.get("localSave", "rangeManagerSub"), rangeManagerSub);
    }

    @Override
    protected void loadLocal() {
        super.loadLocal();
        recoveryManager = AutoTradeUtils.localLoad(Paths.get("localSave", "recoveryManager"));
        log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
        rangeManagerMain = AutoTradeUtils.localLoad(Paths.get("localSave", "rangeManagerMain"));
        rangeManagerSub = AutoTradeUtils.localLoad(Paths.get("localSave", "rangeManagerSub"));
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
    protected void preFix(Snapshot snapshot) {
        if (recoveryManager.isOpen()) {
            rangeManagerSub.save(snapshot);
            if (!snapshot.isSpreadWiden()) {
                rangeManagerMain.save(snapshot);
            }
        }
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
                && snapshot.hasAskProfit()
                && snapshot.isBidGtAsk()
                && rateAnalyzer.isReachedBidThresholdWithin(rate, fixDuration)) {
            fixAsk(snapshot);
            rangeManagerSub.reset();
            recoveryManager.printSummary(snapshot);
            return true;
        }
        if (rateAnalyzer.isAskUp()
                && snapshot.hasBidProfit()
                && snapshot.isBidLtAsk()
                && rateAnalyzer.isReachedAskThresholdWithin(rate, fixDuration)) {
            fixBid(snapshot);
            rangeManagerSub.reset();
            recoveryManager.printSummary(snapshot);
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
                    rangeManagerMain.apply();
                    orderAsk(toInitialLot.applyAsInt(snapshot), snapshot);
                    doAsk = false;
                }
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDuration)) {
                    recoveryManager.open(snapshot);
                    rangeManagerMain.apply();
                    orderBid(toInitialLot.applyAsInt(snapshot), snapshot);
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

            Duration duration = followUpOrderDurationLong;
            if (snapshot.getMoreLot() < toInitialLot.applyAsInt(recoveryManager.getOpenSnapshot()) * 2) {
                duration = followUpOrderDurationShort;
            }

            if (!snapshot.isSpreadWiden()
                    && rangeManagerMain.isSaveExtend()) {
                if (rateAnalyzer.isAskUp()) {
                    if (snapshot.hasAsk()
                            && snapshot.getAskAverageRate() < snapshot.getRate().getAsk()) {
                        return;
                    }
                    if (doAsk
                            && snapshot.isAskLtLimit()
                            && snapshot.isBidLtAsk()
                            && rateAnalyzer.isReachedAskThresholdWithin(rate, duration)) {
                        recoveryManager.setFollowUpSnapshot(snapshot);
                        rangeManagerMain.apply();
                        orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
                        doAsk = false;
                        return;
                    }
                }
                if (rateAnalyzer.isBidDown()) {
                    if (snapshot.hasBid()
                            && snapshot.getBidAverageRate() > snapshot.getRate().getBid()) {
                        return;
                    }
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && snapshot.isBidGtAsk()
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, duration)) {
                        recoveryManager.setFollowUpSnapshot(snapshot);
                        rangeManagerMain.apply();
                        orderBid(toNextLot.applyAsInt(snapshot), snapshot);
                        doBid = false;
                        return;
                    }
                }
            }

            // 反対売買用
            int counterLot = 1;
            if (snapshot.hasOneSide()) {
                counterLot = toInitialLot.applyAsInt(recoveryManager.getOpenSnapshot()) / 2;
                if (counterLot < 1) {
                    counterLot = 1;
                }
            }
            if (snapshot.getMoreLot() >= 2
                    && rangeManagerSub.isSaveExtend()) {
                if (snapshot.hasNoAsk()) {
                    doAsk = true;
                }
                if (rateAnalyzer.isAskUp()) {
                    if (snapshot.hasAsk()
                            && snapshot.getAskAverageRate() < snapshot.getRate().getAsk()) {
                        return;
                    }
                    if (doAsk
                            && snapshot.getBidLot() > snapshot.getAskLot() * 2 + 1
                            && snapshot.isBidGtAsk()
                            && rateAnalyzer.isReachedAskThresholdWithin(rate, counterOrderDuration)) {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        rangeManagerSub.apply();
                        orderAsk(counterLot, snapshot);
                        doAsk = false;
                        return;
                    }
                }
                if (snapshot.hasNoBid()) {
                    doBid = true;
                }
                if (rateAnalyzer.isBidDown()) {
                    if (snapshot.hasBid()
                            && snapshot.getBidAverageRate() > snapshot.getRate().getBid()) {
                        return;
                    }
                    if (doBid
                            && snapshot.getBidLot() * 2 + 1 < snapshot.getAskLot()
                            && snapshot.isBidLtAsk()
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, counterOrderDuration)) {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        rangeManagerSub.apply();
                        orderBid(counterLot, snapshot);
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

        // 反対売買用
        if (snapshot.isBidLtAsk()) {
            if (rateAnalyzer.isAskUp()
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, orderDuration)) {
                doBid = true;
            }
        }
        if (snapshot.isBidGtAsk()) {
            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDuration)) {
                doAsk = true;
            }
        }
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        recoveryManager.printSummary(snapshot);
        recoveryManager.close();
        rangeManagerMain.reset();
        rangeManagerSub.reset();
    }

    @Override
    protected boolean isCalm() {
        return false;
    }

}
