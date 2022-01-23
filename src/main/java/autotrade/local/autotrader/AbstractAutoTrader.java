package autotrade.local.autotrader;

import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.ToIntFunction;

import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.format.annotation.DateTimeFormat;

import autotrade.local.actor.IndicatorManager;
import autotrade.local.actor.PairManager;
import autotrade.local.actor.RateAnalyzer;
import autotrade.local.exception.ApplicationException;
import autotrade.local.material.AudioPath;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Pair;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeUtils;
import autotrade.local.utility.WebDriverWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAutoTrader {

    @Autowired
    protected ApplicationContext applicationContext;

    @Autowired
    protected IndicatorManager indicatorManager;

    @Autowired
    protected PairManager pairManager;

    @Autowired
    protected WebDriverWrapper webDriverWrapper;

    @Autowired
    @Qualifier("pairAnalyzerMap")
    protected Map<String, RateAnalyzer> pairAnalyzerMap;

    protected Pair pair;
    protected RateAnalyzer rateAnalyzer;

    protected int startMargin;
    protected boolean isThroughOrder;
    protected boolean isThroughFix;
    protected boolean isForceException;

    @Value("${autotrade.active.start}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    protected LocalTime activeStart;
    @Value("${autotrade.active.end}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    protected LocalTime activeEnd;

    public AbstractAutoTrader() {
    }

    public void preOperation() {
        pair = pairManager.getDefault();
        rateAnalyzer = pairAnalyzerMap.get(pair.getName());

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("do you need local load? (y or any) :");
            String input = scanner.next();
            if ("y".equals(input.toLowerCase())) {
                loadLocal();
            }
        }

        // シャットダウンフックでローカルセーブ
        Runtime.getRuntime().addShutdownHook(new Thread(() -> saveLocal()));
    }

    protected void saveLocal() {
        AutoTradeUtils.localSave(Paths.get("localSave", "startMargin"), startMargin);
        AutoTradeUtils.localSave(Paths.get("localSave", "pairAnalyzerMap"), pairAnalyzerMap);
    };

    protected void loadLocal() {
        startMargin = AutoTradeUtils.localLoad(Paths.get("localSave", "startMargin"));
        pairAnalyzerMap = AutoTradeUtils.localLoad(Paths.get("localSave", "pairAnalyzerMap"));
        log.info("pairAnalyzerMap loaded.");
        rateAnalyzer = pairAnalyzerMap.get(pair.getName());
    };

    public void operation() {

        try {
            // 初期処理
            initialize();

            // 繰り返し実行
            while (true) {

                // 通貨ペア設定
                changePair(selectPair());

                // 最新情報取得
                Snapshot snapshot = buildSnapshot();
                this.pair = snapshot.getPair();
                this.rateAnalyzer = pairAnalyzerMap.get(this.pair.getName());

                // 取引前処理
                preTrade(snapshot);

                // 取引
                if (isTradable(snapshot)) {
                    trade(snapshot);
                }

                // 取引後処理
                postTrade(snapshot);

                // 強制例外スロー
                if (isForceException) {
                    isForceException = false;
                    throw new ApplicationException("force exception occurred.");
                }

            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            webDriverWrapper.quit();
        }

    }

    protected void initialize() {

        // WebDriver初期化
        webDriverWrapper.setDriver(new ChromeDriver());

        // 指標を確認する
        if (!indicatorManager.hasIndicator()) {
            indicatorManager.addIndicators(webDriverWrapper.getIndicators());
            indicatorManager.printIndicators(LocalDate.now());
            indicatorManager.printIndicators(LocalDate.now().plusDays(1));
        }

        // Web取引ツールの起動から初期設定まで
        webDriverWrapper.initialize();

        // 開始時の証拠金を取得
        if (startMargin == 0) {
            startMargin = AutoTradeUtils.toInt(webDriverWrapper.getMargin());
        }
    }

    protected Snapshot buildSnapshot() {
        return Snapshot.builder()
                .askLot(AutoTradeUtils.toInt(webDriverWrapper.getAskLot()))
                .bidLot(AutoTradeUtils.toInt(webDriverWrapper.getBidLot()))
                .askAverageRate(AutoTradeUtils.toInt(webDriverWrapper.getAskAverageRate()))
                .bidAverageRate(AutoTradeUtils.toInt(webDriverWrapper.getBidAverageRate()))
                .margin(AutoTradeUtils.toInt(webDriverWrapper.getMargin()))
                .effectiveMargin(AutoTradeUtils.toInt(webDriverWrapper.getEffectiveMargin()))
                .todaysProfit(AutoTradeUtils.toInt(webDriverWrapper.getMargin()) - startMargin)
                .rate(buildRate())
                .build();
    }

    protected Rate buildRate() {
        CompletableFuture<String> futureAsk = CompletableFuture.supplyAsync(() -> webDriverWrapper.getAskRate());
        CompletableFuture<String> futureBid = CompletableFuture.supplyAsync(() -> webDriverWrapper.getBidRate());
        String rawAsk = null;
        String rawBid = null;
        try {
            rawAsk = futureAsk.get();
            rawBid = futureBid.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return Rate.builder()
                .pair(pairManager.get(webDriverWrapper.getPair().replace("/", "")))
                .rawAsk(rawAsk)
                .rawBid(rawBid)
                .ask(AutoTradeUtils.toInt(rawAsk))
                .bid(AutoTradeUtils.toInt(rawBid))
                .timestamp(LocalDateTime.now())
                .build();
    }

    protected Rate buildRateFromList(Pair pair) {
        String rawAsk = webDriverWrapper.getAskRateFromList(pair);
        String rawBid = webDriverWrapper.getBidRateFromList(pair);
        return Rate.builder()
                .pair(pair)
                .rawAsk(rawAsk)
                .rawBid(rawBid)
                .ask(AutoTradeUtils.toInt(rawAsk))
                .bid(AutoTradeUtils.toInt(rawBid))
                .timestamp(LocalDateTime.now())
                .build();
    }

    abstract protected Pair selectPair();

    protected void preTrade(Snapshot snapshot) {

        // SnapshotのレートをRateAnalyzerに追加
        pairAnalyzerMap.get(snapshot.getPair().getName()).add(snapshot.getRate());

        changeDisplay(DisplayMode.RATELIST);
        // レートリストから他通貨ペアのレートをRateAnalyzerに追加
        if (snapshot.hasNoPosition()
                || LocalDateTime.now().getSecond() % 10 == 0) {
            pairManager.getPairs().stream()
                    .filter(p -> !p.equals(snapshot.getPair()))
                    .forEach(p -> {
                        pairAnalyzerMap.get(p.getName()).add(buildRateFromList(p));
                    });
        }

        // 非活性時間処理
        if (isSleep(snapshot)) {

            // サマリ出力
            printSummary(snapshot);

            // 非活性時間の終了までスリープする
            Duration durationToActive = Duration.between(LocalTime.now(), activeStart);
            switch (DayOfWeek.from(LocalDate.now())) {
            case SATURDAY:
                durationToActive = Duration.between(LocalDateTime.now(),
                        LocalDateTime.of(LocalDate.now().plusDays(2), activeStart));
                break;
            case SUNDAY:
                durationToActive = Duration.between(LocalDateTime.now(),
                        LocalDateTime.of(LocalDate.now().plusDays(1), activeStart));
                break;
            default:
                break;
            }
            log.info("application will sleep {} minutes, because of inactive time.", durationToActive.toMinutes());
            AutoTradeUtils.sleep(durationToActive);
        }

        if (indicatorManager.isPrevImportant()
                && indicatorManager.isPrevIndicatorWithin(Duration.ofSeconds(15))) {
            rateAnalyzer.clear();
        }

        if (snapshot.isSpreadWiden()
                && indicatorManager.isPrevImportant()
                && indicatorManager.isPrevIndicatorWithin(Duration.ofMinutes(10))) {
            rateAnalyzer.filterNarrow();
        }

    }

    protected boolean isTradable(Snapshot snapshot) {
        if (rateAnalyzer.isDoubtful()) {
            return false;
        }
        return true;
    }

    protected void trade(Snapshot snapshot) {

        preFix(snapshot);
        boolean isFixed = false;
        if (isFixable(snapshot)) {
            // 最新情報を元に利益確定
            isFixed = fix(snapshot);
        }
        postFix(snapshot);

        if (isFixed) {
            return;
        }

        preOrder(snapshot);
        if (isOrderable(snapshot)) {
            // 最新情報を元に注文
            order(snapshot);
        }
        postOrder(snapshot);

    }

    protected void preFix(Snapshot snapshot) {
    }

    protected boolean isFixable(Snapshot snapshot) {
        if (isThroughFix) {
            return false;
        }
        if (snapshot.hasNoPosition()) {
            return false;
        }
        return true;
    }

    abstract protected boolean fix(Snapshot snapshot);

    protected void postFix(Snapshot snapshot) {
    }

    protected void preOrder(Snapshot snapshot) {
    }

    protected boolean isOrderable(Snapshot snapshot) {

        if (isThroughOrder) {
            return false;
        }
        if (!rateAnalyzer.isMoved()) {
            // レートが動いていない場合は注文しない
            return false;
        }
        if (rateAnalyzer.isCalm()) {
            // 閾値間隔が狭い場合は注文しない
            return false;
        }

        switch (snapshot.getStatus()) {
        case NO_POSITION:
        case BID_EQ_ASK:
            if (indicatorManager.isNextImportant()
                    && indicatorManager.isIndicatorAround(Duration.ofSeconds(300), Duration.ofSeconds(15))) {
                // 重要指標が近い場合は注文しない
                return false;
            }
            if (indicatorManager.isIndicatorAround(Duration.ofSeconds(90), Duration.ofSeconds(15))) {
                // 指標が近い場合は注文しない
                return false;
            }
            if (snapshot.isSpreadWiden()) {
                // スプレッドが開いている場合は注文しない
                return false;
            }
            break;
        case BID_LT_ASK:
        case BID_GT_ASK:
            break;
        default:
        }

        int verifyAskLot = AutoTradeUtils.toInt(webDriverWrapper.getAskLot());
        int verifyBidLot = AutoTradeUtils.toInt(webDriverWrapper.getBidLot());
        if (snapshot.getAskLot() != verifyAskLot
                || snapshot.getBidLot() != verifyBidLot) {
            // Fixされている場合は注文しない
            return false;
        }

        return true;
    }

    abstract protected void order(Snapshot snapshot);

    protected void postOrder(Snapshot snapshot) {
    }

    protected void postTrade(Snapshot snapshot) {

        // 指標アラート
        if (indicatorManager.isIndicatorBefore(Duration.ofMinutes(1))) {
            indicatorManager.printNextIndicator();
            AutoTradeUtils.playAudioRandom(AudioPath.Alert);
            AutoTradeUtils.sleep(Duration.ofSeconds(1));
        }

        // お知らせ対策
        if (!isThroughOrder
                && rateAnalyzer.hasDoubtfulRates()) {
            throw new ApplicationException("RateAnalyzer has doubtful rates.");
        }

    }

    protected void printSummary(Snapshot snapshot) {
        log.info("margin summary - start:{} end:{} profit and loss:{}",
                startMargin,
                snapshot.getMargin(),
                snapshot.getMargin() - startMargin);
    }

    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.hasNoPosition()
                && snapshot.isSpreadWiden();
    }

    protected void forceSame(Snapshot snapshot) {
        int askLot = snapshot.getAskLot();
        int bidLot = snapshot.getBidLot();
        if (bidLot < askLot) {
            orderBid(askLot - bidLot, snapshot);
        }
        if (askLot < bidLot) {
            orderAsk(bidLot - askLot, snapshot);
        }
    }

    protected boolean isActiveTime() {
        LocalTime now = LocalTime.now();
        if (activeStart.equals(activeEnd)) {
            return true;
        }
        if (activeStart.equals(now)) {
            return true;
        }
        if (activeStart.isBefore(activeEnd)) {
            return activeStart.isBefore(now) && now.isBefore(activeEnd);
        }
        if (LocalTime.MIN.equals(now)) {
            return true;
        }
        return (activeStart.isBefore(now) && now.isBefore(LocalTime.MAX))
                || (LocalTime.MIN.isBefore(now) && now.isBefore(activeEnd));
    }

    protected boolean isInactiveTime() {
        return !isActiveTime();
    }

    protected void orderAsk(int orderLot, Snapshot snapshot) {
        int afterLot = orderLot + snapshot.getAskLot();
        webDriverWrapper.setLot(orderLot);
        webDriverWrapper.orderAsk();
        verifyOrder(afterLot, Snapshot::getAskLot);
        log.info("{} order ask. lot:{} total lot:{} rate:{}",
                snapshot.getPair().getName(), orderLot, afterLot, snapshot.getRate().getRawAsk());
    }

    protected void orderBid(int orderLot, Snapshot snapshot) {
        int afterLot = orderLot + snapshot.getBidLot();
        webDriverWrapper.setLot(orderLot);
        webDriverWrapper.orderBid();
        verifyOrder(afterLot, Snapshot::getBidLot);
        log.info("{} order bid. lot:{} total lot:{} rate:{}",
                snapshot.getPair().getName(), orderLot, afterLot, snapshot.getRate().getRawBid());
    }

    protected void fixAll(Snapshot snapshot) {
        webDriverWrapper.fixAll();
        AutoTradeUtils.playAudioRandom(AudioPath.FixSoundEffect);
        verifyOrder(0, Snapshot::getAskLot);
        verifyOrder(0, Snapshot::getBidLot);
        log.info("{} fix all position. bid lot:{} ask lot:{} bid rate:{} ask rate:{}",
                snapshot.getPair().getName(),
                snapshot.getBidLot(), snapshot.getAskLot(),
                snapshot.getRate().getRawBid(), snapshot.getRate().getRawAsk());
    }

    protected void fixAsk(Snapshot snapshot) {
        webDriverWrapper.fixAsk();
        verifyOrder(0, Snapshot::getAskLot);
        log.info("{} fix ask position. lot:{} rate:{}",
                snapshot.getPair().getName(),
                snapshot.getAskLot(), snapshot.getRate().getRawBid());
    }

    protected void fixBid(Snapshot snapshot) {
        webDriverWrapper.fixBid();
        verifyOrder(0, Snapshot::getBidLot);
        log.info("{} fix bid position. lot:{} rate:{}",
                snapshot.getPair().getName(),
                snapshot.getBidLot(), snapshot.getRate().getRawAsk());
    }

    protected void verifyOrder(int lot, ToIntFunction<Snapshot> lotAfterOrder) {
        LocalDateTime verifyStarted = LocalDateTime.now();
        while (true) {
            AutoTradeUtils.sleep(Duration.ofMillis(500));
            Snapshot snapshot = buildSnapshot();
            if (lot == lotAfterOrder.applyAsInt(snapshot)) {
                break;
            }
            if (verifyStarted.plus(Duration.ofSeconds(10)).isBefore(LocalDateTime.now())) {
                throw new ApplicationException("verify is failed.");
            }
        }
    }

    protected void changeDisplay(DisplayMode displayMode) {
        switch (displayMode) {
        case CHART:
            webDriverWrapper.displayChart();
            break;
        case RATELIST:
            webDriverWrapper.displayRateList();
            break;
        }
    }

    protected void changePair(Pair pair) {
        if (this.pair.equals(pair)) {
            return;
        }
        this.pair = pair;
        webDriverWrapper.displayRateList();
        webDriverWrapper.changePair(this.pair.getDescription());
        this.rateAnalyzer = this.pairAnalyzerMap.get(this.pair.getName());
        log.info("currency pair is changed to {}.", this.pair.getDescription());
    }

    protected void changeThroughOrder(boolean flag) {
        this.isThroughOrder = flag;
        log.info("through order setting is set {}.", this.isThroughOrder);
    }

    protected void changeThroughFix(boolean flag) {
        this.isThroughFix = flag;
        log.info("through fix setting is set {}.", this.isThroughFix);
    }

}
