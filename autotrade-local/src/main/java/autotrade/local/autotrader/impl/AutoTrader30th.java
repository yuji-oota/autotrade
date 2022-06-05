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
//@Component("autoTrader30th")
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

    @Value("#{T(java.time.Duration).ofSeconds('${autoTrader30th.counterOrder.duration.seconds}')}")
    private Duration counterOrderDuration;

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
        StrageManager.put("recoveryManager", recoveryManager);
    }

    @Override
    protected void loadLocal() {
        super.loadLocal();
        recoveryManager = StrageManager.get("recoveryManager");
        log.info("openSnapshot:{}", recoveryManager.getOpenSnapshot());
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
            return true;
        }
        if (rateAnalyzer.isAskUp()
                && snapshot.hasBidProfit()
                && snapshot.isBidLtAsk()
                && rateAnalyzer.isReachedAskThresholdWithin(rate, fixDuration)) {
            fixBid(snapshot);
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
                    orderAsk(toInitialLot.applyAsInt(snapshot), snapshot);
                    doAsk = false;
                }
                if (rateAnalyzer.isBidDown()
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

            if (rateAnalyzer.isAskUp()) {
                if (doAsk
                        && snapshot.isAskLtLimit()
                        && snapshot.isBidLtAsk()
                        && rate.isAskLt(recoveryManager.getLastFollowUpAskSnapshot().getRate())
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, followUpOrderDuration)) {
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    recoveryManager.setLastFollowUpAskSnapshot(snapshot);
                    orderAsk(toNextLot.applyAsInt(snapshot), snapshot);
                    doAsk = false;
                    return;
                }
            }
            if (rateAnalyzer.isBidDown()) {
                if (doBid
                        && snapshot.isBidLtLimit()
                        && snapshot.isBidGtAsk()
                        && rate.isBidGt(recoveryManager.getLastFollowUpBidSnapshot().getRate())
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, followUpOrderDuration)) {
                    recoveryManager.setCounterTradingSnapshot(snapshot);
                    recoveryManager.setLastFollowUpBidSnapshot(snapshot);
                    orderBid(toNextLot.applyAsInt(snapshot), snapshot);
                    doBid = false;
                    return;
                }
            }

            // 反対売買用
            if (snapshot.getMoreLot() < 2) {
                return;
            }
            if (snapshot.hasNoAsk()) {
                doAsk = true;
                recoveryManager.getLastFollowUpAskSnapshot().setRate(rate.toBuilder().ask(Integer.MAX_VALUE).build());
            }
            if (rateAnalyzer.isAskUp()) {
                if (doAsk
                        && snapshot.getBidLot() > snapshot.getAskLot() * 2 + 1
                        && snapshot.isBidGtAsk()
                        && rate.isAskLt(recoveryManager.getLastFollowUpAskSnapshot().getRate())
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
            if (rateAnalyzer.isBidDown()) {
                if (doBid
                        && snapshot.getBidLot() * 2 + 1 < snapshot.getAskLot()
                        && snapshot.isBidLtAsk()
                        && rate.isBidGt(recoveryManager.getLastFollowUpBidSnapshot().getRate())
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
        int counterLot = 1;
        if (snapshot.hasOneSide()) {
            counterLot = snapshot.getMoreLot() / 4;
            if (counterLot < 1) {
                counterLot = 1;
            }
        }
        return counterLot;
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

        if (recoveryManager.isOpen()) {
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
    }

    @Override
    protected boolean isCalm() {
        return false;
    }

}
