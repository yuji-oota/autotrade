package autotrade.local.autotrader.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.AbstractMap.SimpleEntry;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.function.ToIntFunction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import autotrade.local.actor.RangeManager;
import autotrade.local.actor.RecoveryManager;
import autotrade.local.actor.StrageManager;
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
@Component("autoTrader31th")
@Slf4j
public class AutoTrader31th extends AbstractAutoTrader {

    private boolean doAsk;
    private boolean doBid;

    @Autowired
    private RecoveryManager recoveryManager;

    @Autowired
    private RangeManager rangeManager;

    @Autowired
    private ToIntFunction<Snapshot> toMinimumProfit;

    @Autowired
    private ToIntFunction<Snapshot> toInitialLot;

    @Autowired
    private ToIntFunction<Snapshot> toNextLot;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader31th.order.duration.seconds}')}")
    private Duration orderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader31th.counterOrder.duration.seconds}')}")
    private Duration counterOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader31th.followUpOrder.duration.seconds}')}")
    private Duration followUpOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader31th.doOrder.duration.seconds}')}")
    private Duration doOrderDuration;

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader31th.fix.duration.seconds}')}")
    private Duration fixDuration;

    private boolean doFollowUp;
    private boolean hasRangeBreakLot;

    public AutoTrader31th() {
        super();
        log.info("autoTrader31th started.");
        doFollowUp = true;
    }

    @Override
    protected void saveLocal() {
        super.saveLocal();
        StrageManager.put("recoveryManager", recoveryManager);
        StrageManager.put("rangeManager", rangeManager);
        StrageManager.put("doFollowUp", doFollowUp);
        StrageManager.put("hasRangeBreakLot", hasRangeBreakLot);
    }

    @Override
    protected void loadLocal() {
        super.loadLocal();
        recoveryManager = StrageManager.get("recoveryManager");
        log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
        rangeManager = StrageManager.get("rangeManager");
        log.info("rangeManager.upperLimitSave:{}", rangeManager.getUpperLimitSave());
        log.info("rangeManager.lowerLimitSave:{}", rangeManager.getLowerLimitSave());
        doFollowUp = StrageManager.get("doFollowUp");
        log.info("doFollowUp:{}", doFollowUp);
        hasRangeBreakLot = StrageManager.get("hasRangeBreakLot");
        log.info("hasRangeBreakLot:{}", hasRangeBreakLot);
    }

    @SuppressWarnings("resource")
    @Override
    protected void scanProcess() {
        super.scanProcess();
        if (recoveryManager.isOpen()) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("adjust open snapshot margin if necessary :");
            String input = scanner.nextLine();
            if (AutoTradeUtils.isNumeric(input)) {
                int adjustMargin = Integer.valueOf(input);

                log.info("before adjust");
                log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());

                int beforeMargin = recoveryManager.getOpenSnapshot().getMargin();
                int afterMargin = beforeMargin + adjustMargin;
                recoveryManager.getOpenSnapshot().setMargin(afterMargin);
                recoveryManager.getOpenSnapshot().setEffectiveMargin(afterMargin);

                log.info("after adjust");
                log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
            } else {
                log.info("adjust open snapshot skipped because input value is not numeric value:{}", input);
            }

        }
    }

    @Override
    protected void addMessageHandler() {
        super.addMessageHandler();
        jmsMessageListener.addHandler("doFollowUp", s -> {
            doFollowUp = Boolean.valueOf(s);
            log.info("doFollowUp is changed to {}", doFollowUp);
        });
        jmsMessageListener.addHandler("hasRangeBreakLot", s -> {
            hasRangeBreakLot = Boolean.valueOf(s);
            log.info("hasRangeBreakLot is changed to {}", hasRangeBreakLot);
        });
        jmsMessageListener.addHandler("forceFollowUpAsk", s -> {
            Rate rate = recoveryManager.getLastFollowUpAskSnapshot().getRate();
            recoveryManager.getLastFollowUpAskSnapshot().setRate(rate.toBuilder().ask(Integer.MAX_VALUE).build());
        });
        jmsMessageListener.addHandler("forceFollowUpBid", s -> {
            Rate rate = recoveryManager.getLastFollowUpBidSnapshot().getRate();
            recoveryManager.getLastFollowUpBidSnapshot().setRate(rate.toBuilder().bid(Integer.MIN_VALUE).build());
        });
        jmsMessageListener.addHandler("forceOpenRecoveryManager", s -> {
            recoveryManager.open(buildSnapshot());
            recoveryManager.setLastFollowUpAskSnapshot(buildSnapshot());
            recoveryManager.setLastFollowUpBidSnapshot(buildSnapshot());
        });
        jmsMessageListener.addHandler("updateOpenSnapshotMargin", s -> {
            int adjustMargin = Integer.valueOf(s);
            log.info("before update");
            log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
            recoveryManager.getOpenSnapshot().setMargin(adjustMargin);
            recoveryManager.getOpenSnapshot().setEffectiveMargin(adjustMargin);
            log.info("after update");
            log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
        });
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
        return snapshot.hasNoPosition()
                && isInactiveTime();
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
                && snapshot.hasAskProfit()
                && snapshot.isBidGtAsk()
                && rateAnalyzer.isReachedBidThresholdWithin(rate, fixDuration)) {
            fixAsk(snapshot);
            recoveryManager.printSummary(snapshot);
            hasRangeBreakLot = false;
            return true;
        }
        if (rateAnalyzer.isAskUp()
                && snapshot.hasBidProfit()
                && snapshot.isBidLtAsk()
                && rateAnalyzer.isReachedAskThresholdWithin(rate, fixDuration)) {
            fixBid(snapshot);
            recoveryManager.printSummary(snapshot);
            hasRangeBreakLot = false;
            return true;
        }

        // 反対売買レンジブレイク用
        if (hasRangeBreakLot) {
            if (snapshot.isBidGtAsk()
                    && snapshot.getBidPipProfit() > snapshot.getAskPipProfit()) {
                fixAsk(snapshot);
                recoveryManager.printSummary(snapshot);
                hasRangeBreakLot = false;
                return true;
            }
            if (snapshot.isBidLtAsk()
                    && snapshot.getBidPipProfit() < snapshot.getAskPipProfit()) {
                fixBid(snapshot);
                recoveryManager.printSummary(snapshot);
                hasRangeBreakLot = false;
                return true;
            }
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

                rangeManager.reset();
                rangeManager.save(snapshot);
                int min = rateAnalyzer.minWithin(orderDuration);
                int max = rateAnalyzer.maxWithin(orderDuration);
                if (Integer.MIN_VALUE < min && max < Integer.MAX_VALUE) {
                    rangeManager.adjustTermination(min, max);
                }

                // 前日比取得
                int rateDiff = AutoTradeUtils.toInt(webDriverWrapper.getRateDiffFromList(snapshot.getPair()));

                if (rateAnalyzer.isAskUp()
                        && rateDiff > 0
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, orderDuration)) {
                    recoveryManager.open(snapshot);
                    orderAsk(toInitialLot.applyAsInt(snapshot), snapshot);
                    doAsk = false;
                }
                if (rateAnalyzer.isBidDown()
                        && rateDiff < 0
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, orderDuration)) {
                    recoveryManager.open(snapshot);
                    orderBid(toInitialLot.applyAsInt(snapshot), snapshot);
                    doBid = false;
                }
                recoveryManager.setLastFollowUpAskSnapshot(snapshot);
                recoveryManager.setLastFollowUpBidSnapshot(snapshot);
            }

            break;
        case BID_LT_ASK:
            // 買いポジションが多い場合
        case BID_GT_ASK:
            // 売りポジションが多い場合
        case BID_EQ_ASK:
            // ポジションが同数の場合

            if (doFollowUp) {

                int orderDiff = 25;
                if (snapshot.getLimitLot() / 10 <= snapshot.getMoreLot()) {
                    orderDiff = 100;
                }

                if (rateAnalyzer.isAskUp()) {
                    if (doAsk
                            && snapshot.isAskLtLimit()
                            && snapshot.isBidLtAsk()
                            && rate.isAskLe(recoveryManager.getLastFollowUpAskSnapshot().getRate().getAsk() - orderDiff)
                            && rateAnalyzer.isReachedAskThresholdWithin(rate, followUpOrderDuration)) {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        recoveryManager.setLastFollowUpAskSnapshot(snapshot);
                        //                        orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
                        orderAsk(1, snapshot);
                        doAsk = false;
                        return;
                    }
                }
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.isBidLtLimit()
                            && snapshot.isBidGtAsk()
                            && rate.isBidGe(recoveryManager.getLastFollowUpBidSnapshot().getRate().getBid() + orderDiff)
                            && rateAnalyzer.isReachedBidThresholdWithin(rate, followUpOrderDuration)) {
                        recoveryManager.setCounterTradingSnapshot(snapshot);
                        recoveryManager.setLastFollowUpBidSnapshot(snapshot);
                        //                        orderBid(toNextLot.applyAsInt(snapshot), snapshot);
                        orderBid(1, snapshot);
                        doBid = false;
                        return;
                    }
                }
            }

            // 反対売買用
            rangeManager.save(snapshot);

            if (snapshot.getMoreLot() < 2) {
                return;
            }
            if (snapshot.hasNoAsk()) {
                doAsk = true;
                recoveryManager.getLastFollowUpAskSnapshot().setRate(rate.toBuilder().ask(Integer.MAX_VALUE).build());
            }
            if (rateAnalyzer.isAskUp()
                    && snapshot.isBidGtAsk()) {

                // レンジブレイク用
                if (rangeManager.getUpperLimitSave() == rate
                        && snapshot.getAskLot() < toRangeBreakLot(snapshot)) {
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    if (snapshot.hasNoAsk()) {
                        recoveryManager.setLastFollowUpAskSnapshot(snapshot);
                    }
                    orderAsk(toRangeBreakLot(snapshot) - snapshot.getAskLot(), snapshot);
                    doAsk = false;
                    hasRangeBreakLot = true;
                    return;
                }

                if (doAsk
                        && !hasRangeBreakLot
                        && snapshot.getMoreLot() > snapshot.getLessLot() * 2 + 1
                        && rate.isAskLe(recoveryManager.getLastFollowUpAskSnapshot().getRate().getAsk() - 25)
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, counterOrderDuration)) {
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    recoveryManager.setLastFollowUpAskSnapshot(snapshot);
                    orderAsk(toCounterLot(snapshot), snapshot);
                    doAsk = false;
                    return;
                }
            }
            if (snapshot.hasNoBid()) {
                doBid = true;
                recoveryManager.getLastFollowUpBidSnapshot().setRate(rate.toBuilder().bid(Integer.MIN_VALUE).build());
            }
            if (rateAnalyzer.isBidDown()
                    && snapshot.isBidLtAsk()) {

                // レンジブレイク用
                if (rangeManager.getLowerLimitSave() == rate
                        && snapshot.getBidLot() < toRangeBreakLot(snapshot)) {
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    if (snapshot.hasNoBid()) {
                        recoveryManager.setLastFollowUpBidSnapshot(snapshot);
                    }
                    orderBid(toRangeBreakLot(snapshot) - snapshot.getBidLot(), snapshot);
                    doBid = false;
                    hasRangeBreakLot = true;
                    return;
                }

                if (doBid
                        && !hasRangeBreakLot
                        && snapshot.getMoreLot() > snapshot.getLessLot() * 2 + 1
                        && rate.isBidGe(recoveryManager.getLastFollowUpBidSnapshot().getRate().getBid() + 25)
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, counterOrderDuration)) {
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    recoveryManager.setLastFollowUpBidSnapshot(snapshot);
                    orderBid(toCounterLot(snapshot), snapshot);
                    doBid = false;
                    return;
                }
            }

            break;
        default:
        }

    }

    private int toCounterLot(Snapshot snapshot) {
        return 1;
    }

    private int toRangeBreakLot(Snapshot snapshot) {
        int rangeBreakLot = snapshot.getMoreLot() / 4;
        if (rangeBreakLot < 1) {
            rangeBreakLot = 1;
        }
        return rangeBreakLot;
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
                    && rateAnalyzer.isReachedAskThresholdWithin(rate, counterOrderDuration)) {
                doBid = true;
            }
        }
        if (snapshot.isBidGtAsk()) {
            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThresholdWithin(rate, counterOrderDuration)) {
                doAsk = true;
            }
        }

        if (recoveryManager.isOpen()
                && snapshot.getMoreLot() <= snapshot.getLessLot() * 2 + 1) {
            LocalDateTime now = LocalDateTime.now();
            Snapshot lastFollowUpAskSnapshot = recoveryManager.getLastFollowUpAskSnapshot();
            if (Objects.nonNull(lastFollowUpAskSnapshot)
                    && lastFollowUpAskSnapshot.getRate().getTimestamp().isBefore(now.minusHours(1))) {
                recoveryManager.getLastFollowUpAskSnapshot().setRate(rate.toBuilder().ask(Integer.MAX_VALUE).build());
            }
            Snapshot lastFollowUpBidSnapshot = recoveryManager.getLastFollowUpBidSnapshot();
            if (Objects.nonNull(lastFollowUpBidSnapshot)
                    && lastFollowUpBidSnapshot.getRate().getTimestamp().isBefore(now.minusHours(1))) {
                recoveryManager.getLastFollowUpBidSnapshot().setRate(rate.toBuilder().bid(Integer.MIN_VALUE).build());
            }
        }
    }

    @Override
    protected void fixAll(Snapshot snapshot) {
        super.fixAll(snapshot);
        recoveryManager.printSummary(snapshot);
        recoveryManager.close();
        doFollowUp = true;
    }

    @Override
    protected boolean isCalm() {
        return false;
    }

}
