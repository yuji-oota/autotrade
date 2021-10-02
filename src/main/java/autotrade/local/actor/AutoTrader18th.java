package autotrade.local.actor;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import org.openqa.selenium.By;

import autotrade.local.material.CurrencyPair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader18th extends AutoTrader {

    private RecoveryManager recoveryManager;
    private Rate lastDayBeforeRate;
    private Set<CurrencyPair> recommendedPairs;
    private boolean doAsk;
    private boolean doBid;
    private Duration counterDuration;

    public AutoTrader18th() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTrader18th.rateAnalizer.threshold.seconds")));
        });
        recommendedPairs = AutoTradeProperties.getList("autoTrader18th.autoRecommended.pairs").stream()
                .map(CurrencyPair::valueOf)
                .collect(Collectors.toSet());
        counterDuration = Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTrader18th.counter.duration.seconds"));

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
            }
        }
        ;

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"),
                            recoveryManager.getSnapshotWhenStart());
                }));
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.isPositionNone();
    }

    private void updateLastDayBeforeRate() {
        String theDayBeforeDiff = driver.findElement(By.xpath("//*[@id=\"hl-div\"]/span[5]")).getText();
        lastDayBeforeRate = Rate.builder().ask(0).bid(0).timestamp(LocalDateTime.now()).build();
        int lastDayBeforeBid = AutoTradeUtils.toInt(theDayBeforeDiff.substring(1));
        if ("▼".equals(theDayBeforeDiff.substring(0, 1))) {
            lastDayBeforeBid = lastDayBeforeBid * -1;
        }
        Snapshot snapshot = buildSnapshot();
        lastDayBeforeRate.setBid(snapshot.getRate().getBid() - lastDayBeforeBid);
        lastDayBeforeRate.setAsk(lastDayBeforeRate.getBid() + pair.getMinSpread());
    }

    @Override
    protected void initialize() {
        super.initialize();
        updateLastDayBeforeRate();
    }

    @Override
    protected void changeRecommended() {
        CurrencyPair recommended = recommendedPairs.stream().map(pair -> {
            return new AbstractMap.SimpleEntry<CurrencyPair, Integer>(
                    pair, Math.abs(AutoTradeUtils.toInt(wrapper.getRateDiffFromList(pair))));
        })
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .get()
                .getKey();
        this.changePair(recommended);
        updateLastDayBeforeRate();
    }

//    @Override
//    protected boolean isOrderable(Snapshot snapshot) {
//        boolean isOrderable = super.isOrderable(snapshot);
//        if (isOrderable) {
//            if (isCalm()) {
//                isOrderable = false;
//            }
//        }
//        return isOrderable;
//    }

    @Override
    protected void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int initialLot = snapshot.getMargin() / 100000;

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合

            if (isPlusTheDayBefore(snapshot.getRate().getBid())) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThreshold(rate)) {
                    orderAsk(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doAsk = false;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    doBid = false;
                }
            }

            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
        case BID_SIDE:
            // 売りポジションが多い場合
        case SAME:
            // ポジションが同数の場合

            if (isPlusTheDayBefore(snapshot.getRate().getBid())) {
                if (rateAnalyzer.isAskUp()) {
                    if (doAsk
                            && snapshot.getAskLot() < lotManager.getLimit()
                            && rateAnalyzer.isReachedAskThreshold(rate)) {
                        if (snapshot.isAskLtBid()) {
                            forceSame(snapshot);
                        } else {
                            orderAsk(1);
                        }
                        doAsk = false;
                        return;
                    }
                }
//                if (rateAnalyzer.isBidDown()) {
//                    if (snapshot.isBidLtAsk()
//                            && rateAnalyzer.isReachedBidThresholdWithin(rate, counterDuration)) {
//                        forceSame(snapshot);
//                        return;
//                    }
//                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (doBid
                            && snapshot.getBidLot() < lotManager.getLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        if (snapshot.isBidLtAsk()) {
                            forceSame(snapshot);
                        } else {
                            orderBid(1);
                        }
                        doBid = false;
                        return;
                    }
                }
//                if (rateAnalyzer.isAskUp()) {
//                    if (snapshot.isAskLtBid()
//                            && rateAnalyzer.isReachedAskThresholdWithin(rate, counterDuration)) {
//                        forceSame(snapshot);
//                        return;
//                    }
//                }
            }

            break;
        default:
        }

    }

    @Override
    protected void postOrder(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        if (rateAnalyzer.isAskUp()
                && rateAnalyzer.isReachedAskThreshold(rate)) {
            doBid = true;
        }
        if (rateAnalyzer.isBidDown()
                && rateAnalyzer.isReachedBidThreshold(rate)) {
            doAsk = true;
        }
    }

    private boolean isPlusTheDayBefore(int bid) {
        return lastDayBeforeRate.getBid() < bid;
    }

    @Override
    protected boolean isCalm() {
        return rateAnalyzer.rangeWithin(Duration.ofSeconds(150)) < 25;
    }

    @Override
    protected boolean isFixable(Snapshot snapshot) {
        boolean isFixable = super.isFixable(snapshot);
        if (isFixable
                && snapshot.isPositionSame()
                && lotManager.isLimit(snapshot)) {
            isFixable = false;
        }
        return isFixable;
    }

    @Override
    protected void fix(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        int targetProfit = snapshot.getMargin() / 10000;

        if (recoveryManager.isOpen()
                && recoveryManager.isRecoveredWithProfit(snapshot, targetProfit)) {
            if (rateAnalyzer.isBidDown()
                    && snapshot.isPositionAskSide()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                fixAll(snapshot);
            }
            if (rateAnalyzer.isAskUp()
                    && snapshot.isPositionBidSide()
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                fixAll(snapshot);
            }
            return;
        }

        if (snapshot.hasBothSide()) {
            if (isPlusTheDayBefore(snapshot.getRate().getBid())) {
                if (rateAnalyzer.isAskUp()
                        && rateAnalyzer.isReachedAskThresholdWithin(rate, counterDuration)) {
                    fixBid(snapshot);
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThresholdWithin(rate, counterDuration)) {
                    fixAsk(snapshot);
                }
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
    protected MessageListener customizeMessageListener() {
        MessageListener messageListener = super.customizeMessageListener();
        return messageListener;
    }

}
