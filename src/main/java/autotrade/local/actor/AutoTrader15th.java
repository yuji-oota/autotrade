package autotrade.local.actor;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Scanner;

import org.openqa.selenium.By;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader15th extends AutoTrader {

    private enum OrderDirection {ASK, BID, NONE}

    private RecoveryManager recoveryManager;
    private OrderDirection orderDirection;
    private Rate lastDayBeforeRate;

    public AutoTrader15th() {
        super();
        recoveryManager = new RecoveryManager();
        pairAnalyzerMap.values().stream().forEach(analyzer -> {
            analyzer.setThresholdDuration(
                    Duration.ofSeconds(
                            AutoTradeProperties.getInt("autoTrader15th.rateAnalizer.threshold.seconds")));
        });

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshotWhenRecoveryStart"));
                recoveryManager.open(snapshot);
            }
        };

        //シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                AutoTradeUtils.localSave(Paths.get("localSave", "snapshotWhenRecoveryStart"), recoveryManager.getSnapshotWhenStart());
            }
        ));
    }

    @Override
    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.isPositionNone();
    }

    @Override
    protected void initialize() {
        super.initialize();
        String theDayBeforeDiff = driver.findElement(By.xpath("//*[@id=\"hl-div\"]/span[5]")).getText();
        lastDayBeforeRate = Rate.builder().ask(0).bid(0).timestamp(LocalDateTime.now()).build();
        int lastDayBeforeBid = AutoTradeUtils.toInt(theDayBeforeDiff.substring(1));
        if ("▼".equals(theDayBeforeDiff.substring(0, 1))) {
            lastDayBeforeBid = lastDayBeforeBid * -1;
        }
        Snapshot snapshot = buildSnapshot();
        lastDayBeforeRate.setBid(snapshot.getRate().getBid() - lastDayBeforeBid);
        lastDayBeforeRate.setAsk(lastDayBeforeRate.getBid() + pair.getMinSpread());
        log.info("lastDayBeforeRate:{}", lastDayBeforeRate);
    }

    @Override
    protected boolean isOrderable(Snapshot snapshot) {
        boolean isOrderable = super.isOrderable(snapshot);
        if (isOrderable) {
            if (isCalm()) {
                isOrderable = false;
            }
            if (snapshot.isPositionSame()
                    && Math.abs(lastDayBeforeRate.getBid() - snapshot.getRate().getBid()) < 100) {
                isOrderable = false;
            }
        }
        return isOrderable;
    }

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
                    orderDirection = OrderDirection.ASK;
                }
            } else {
                if (rateAnalyzer.isBidDown()
                        && rateAnalyzer.isReachedBidThreshold(rate)) {
                    orderBid(initialLot);
                    recoveryManager.close();
                    recoveryManager.open(snapshot);
                    orderDirection = OrderDirection.BID;
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
                    if (orderDirection == OrderDirection.BID
                            && snapshot.getAskLot() < lotManager.getLimit()
                            && rateAnalyzer.isReachedAskThreshold(rate)) {
                        orderAsk(1);
                        orderDirection = OrderDirection.ASK;
                    }
                    if (snapshot.getAskLot() < snapshot.getBidLot()
                            && rateAnalyzer.isReachedAskThreshold(rate)) {
                        forceSame(snapshot);
                    }
                }
            } else {
                if (rateAnalyzer.isBidDown()) {
                    if (orderDirection == OrderDirection.ASK
                            && snapshot.getBidLot() < lotManager.getLimit()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        orderBid(1);
                        orderDirection = OrderDirection.BID;
                    }
                    if (snapshot.getAskLot() > snapshot.getBidLot()
                            && rateAnalyzer.isReachedBidThreshold(rate)) {
                        forceSame(snapshot);
                    }
                }
            }

            if (rateAnalyzer.isAskUp()
                    && rateAnalyzer.isReachedAskThreshold(rate)) {
                orderDirection = OrderDirection.ASK;
            }
            if (rateAnalyzer.isBidDown()
                    && rateAnalyzer.isReachedBidThreshold(rate)) {
                orderDirection = OrderDirection.BID;
            }

        default:
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

        if (recoveryManager.isOpen()
                && recoveryManager.isRecoveredWithProfit(snapshot, snapshot.getMargin() / 10000)) {
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
