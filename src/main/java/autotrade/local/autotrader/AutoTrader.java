package autotrade.local.autotrader;

import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.actor.IndicatorManager;
import autotrade.local.actor.RateAnalyzer;
import autotrade.local.exception.ApplicationException;
import autotrade.local.material.AudioPath;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import autotrade.local.utility.WebDriverHirose;
import autotrade.local.utility.WebDriverWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AutoTrader {

    protected CurrencyPair pair;
    protected List<CurrencyPair> orderablePairs;

    protected WebDriver driver;
    protected WebDriverWrapper wrapper;
    protected Map<CurrencyPair, RateAnalyzer> pairAnalyzerMap;
    protected RateAnalyzer rateAnalyzer;
    protected IndicatorManager indicatorManager;

    protected int startMargin;

    protected LocalTime inactiveStart;
    protected LocalTime inactiveEnd;

    protected boolean isThroughOrder;
    protected boolean isThroughFix;
    protected boolean isIgnoreSpread;
    protected boolean isForceException;

    public AutoTrader() {
        pair = CurrencyPair.USDJPY;
        orderablePairs = AutoTradeProperties.getList("autotrade.order.pairs").stream()
                .map(CurrencyPair::valueOf)
                .toList();
        inactiveStart = LocalTime
                .from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime
                .from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));

        pairAnalyzerMap = Stream.of(CurrencyPair.values())
                .collect(Collectors.toMap(pair -> pair, pair -> new RateAnalyzer()));
        rateAnalyzer = pairAnalyzerMap.get(pair);
        indicatorManager = new IndicatorManager();
    }

    protected void postConstruct() {
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
        AutoTradeUtils.localSave(Paths.get("localSave", "pairAnalyzerMap"), pairAnalyzerMap);
    };

    protected void loadLocal() {
        pairAnalyzerMap = AutoTradeUtils.localLoad(Paths.get("localSave", "pairAnalyzerMap"));
        log.info("pairAnalyzerMap loaded.");
        rateAnalyzer = pairAnalyzerMap.get(pair);
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

                // 取引前処理
                preTrade(snapshot);

                // 取引
                if (isTradable(snapshot)) {
                    trade(snapshot);
                }

                // 取引後処理
                postTrade(snapshot);

                // メッセージダイアログクローズ
                wrapper.cancelMessage();

                // 強制例外スロー
                if (isForceException) {
                    isForceException = false;
                    throw new ApplicationException("force exception occurred.");
                }

            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            driver.quit();
        }

    }

    protected void initialize() {
        // WebDriver初期化
        driver = new ChromeDriver();
        wrapper = new WebDriverHirose(driver);

        // 指標を確認する
        if (!indicatorManager.hasIndicator()) {
            indicatorManager.addIndicators(wrapper.getIndicators());
            indicatorManager.printIndicatorDateTimes();
        }

        // ログイン
        wrapper.login();
        AutoTradeUtils.sleep(Duration.ofSeconds(5));

        // メッセージダイアログクローズ
        wrapper.cancelMessage();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        // ツール起動
        wrapper.startUpTradeTool();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        // 取引設定
        wrapper.orderSettings();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        // 開始時の証拠金を取得
        if (startMargin == 0) {
            startMargin = AutoTradeUtils.toInt(wrapper.getMargin());
        }

        // 通貨ペア定義
        wrapper.pairSettings();
    }

    protected Snapshot buildSnapshot() {
        return Snapshot.builder()
                .askLot(AutoTradeUtils.toInt(wrapper.getAskLot()))
                .bidLot(AutoTradeUtils.toInt(wrapper.getBidLot()))
                .askAverageRate(AutoTradeUtils.toInt(wrapper.getAskAverageRate()))
                .bidAverageRate(AutoTradeUtils.toInt(wrapper.getBidAverageRate()))
                .margin(AutoTradeUtils.toInt(wrapper.getMargin()))
                .effectiveMargin(AutoTradeUtils.toInt(wrapper.getEffectiveMargin()))
                .todaysProfit(AutoTradeUtils.toInt(wrapper.getMargin()) - startMargin)
                .rate(buildRate())
                .build();
    }

    protected Rate buildRate() {
        CompletableFuture<String> futureAsk = CompletableFuture.supplyAsync(() -> wrapper.getAskRate());
        CompletableFuture<String> futureBid = CompletableFuture.supplyAsync(() -> wrapper.getBidRate());
        String rawAsk = null;
        String rawBid = null;
        try {
            rawAsk = futureAsk.get();
            rawBid = futureBid.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return Rate.builder()
                .pair(CurrencyPair.valueOf(wrapper.getPair().replace("/", "")))
                .rawAsk(rawAsk)
                .rawBid(rawBid)
                .ask(AutoTradeUtils.toInt(rawAsk))
                .bid(AutoTradeUtils.toInt(rawBid))
                .timestamp(LocalDateTime.now())
                .build();
    }

    protected Rate buildRateFromList(CurrencyPair pair) {
        String rawAsk = wrapper.getAskRateFromList(pair);
        String rawBid = wrapper.getBidRateFromList(pair);
        return Rate.builder()
                .pair(pair)
                .rawAsk(rawAsk)
                .rawBid(rawBid)
                .ask(AutoTradeUtils.toInt(rawAsk))
                .bid(AutoTradeUtils.toInt(rawBid))
                .timestamp(LocalDateTime.now())
                .build();
    }

    abstract protected CurrencyPair selectPair();

    protected void preTrade(Snapshot snapshot) {

        // SnapshotのレートをRateAnalyzerに追加
        pairAnalyzerMap.get(snapshot.getPair()).add(snapshot.getRate());

        if (orderablePairs.size() > 1) {

            changeDisplay(DisplayMode.RATELIST);
            // レートリストから他通貨ペアのレートをRateAnalyzerに追加
            if (snapshot.hasNoPosition()
                    || LocalDateTime.now().getSecond() % 10 == 0) {
                orderablePairs.stream()
                        .filter(p -> p != snapshot.getPair())
                        .forEach(p -> {
                            pairAnalyzerMap.get(p).add(buildRateFromList(p));
                        });
            }
        }

        // 非活性時間処理
        if (isSleep(snapshot)) {

            // サマリ出力
            printSummary(snapshot);

            // 非活性時間の終了までスリープする
            Duration durationToActive = Duration.between(LocalDateTime.now(),
                    LocalDateTime.of(LocalDate.now(), inactiveEnd));
            log.info("application will sleep {} minutes, because of inactive time.", durationToActive.toMinutes());
            AutoTradeUtils.sleep(durationToActive);
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
        if (isFixable(snapshot)) {
            // 最新情報を元に利益確定
            fix(snapshot);
        }
        postFix(snapshot);

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

    abstract protected void fix(Snapshot snapshot);

    protected void postFix(Snapshot snapshot) {
    }

    protected void preOrder(Snapshot snapshot) {
    }

    protected boolean isOrderable(Snapshot snapshot) {
        if (snapshot.isFix()) {
            return false;
        }
        if (isThroughOrder) {
            return false;
        }
        if (Duration.between(rateAnalyzer.getEarliestRate().getTimestamp(), LocalDateTime.now()).toMinutes() < 1) {
            // 過去Rateがある程度存在しない場合は注文しない
            return false;
        }
        if (!rateAnalyzer.isMoved()) {
            // レートが動いていない場合は注文しない
            return false;
        }

        switch (snapshot.getStatus()) {
        case NO_POSITION:
        case BID_EQ_ASK:
            if (rateAnalyzer.isCalm()) {
                // 閾値間隔が狭い場合は注文しない
                return false;
            }
            if (indicatorManager.isIndicatorAround(Duration.ofSeconds(90), Duration.ofSeconds(15))) {
                // 指標が近い場合は注文しない
                return false;
            }
            if (!isIgnoreSpread && snapshot.isSpreadWiden()) {
                // スプレッドを無視しない
                // 且つスプレッドが開いている場合は注文しない
                return false;
            }
            break;
        case BID_LT_ASK:
        case BID_GT_ASK:
            break;
        default:
        }

        int verifyAskLot = AutoTradeUtils.toInt(wrapper.getAskLot());
        int verifyBidLot = AutoTradeUtils.toInt(wrapper.getBidLot());
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
        log.info("below is a summary.");
        AutoTradeUtils.printObject(snapshot);
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

    protected boolean isInactiveTime() {
        return inactiveStart.isBefore(LocalTime.now()) && LocalTime.now().isBefore(inactiveEnd);
    }

    protected void orderAsk(int orderLot, Snapshot snapshot) {
        int afterLot = orderLot + snapshot.getAskLot();
        wrapper.setLot(orderLot);
        wrapper.orderAsk();
        verifyOrder(afterLot, Snapshot::getAskLot);
        log.info("{} order ask. lot:{} total lot:{} rate:{}",
                snapshot.getPair(), orderLot, afterLot, snapshot.getRate().getRawAsk());
    }

    protected void orderBid(int orderLot, Snapshot snapshot) {
        int afterLot = orderLot + snapshot.getBidLot();
        wrapper.setLot(orderLot);
        wrapper.orderBid();
        verifyOrder(afterLot, Snapshot::getBidLot);
        log.info("{} order bid. lot:{} total lot:{} rate:{}",
                snapshot.getPair(), orderLot, afterLot, snapshot.getRate().getRawBid());
    }

    protected void fixAll(Snapshot snapshot) {
        wrapper.fixAll();
        AutoTradeUtils.playAudioRandom(AudioPath.FixSoundEffect);
        verifyOrder(0, Snapshot::getAskLot);
        verifyOrder(0, Snapshot::getBidLot);
        log.info("fix all position.");
    }

    protected void fixAsk(Snapshot snapshot) {
        wrapper.fixAsk();
        verifyOrder(0, Snapshot::getAskLot);
        log.info("fix ask position.");
    }

    protected void fixBid(Snapshot snapshot) {
        wrapper.fixBid();
        verifyOrder(0, Snapshot::getBidLot);
        log.info("fix bid position.");
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
            wrapper.displayChart();
            break;
        case RATELIST:
            wrapper.displayRateList();
            break;
        }
    }

    protected void changePair(CurrencyPair pair) {
        if (this.pair == pair) {
            return;
        }
        this.pair = pair;
        wrapper.displayRateList();
        wrapper.changePair(this.pair.getDescription());
        this.rateAnalyzer = this.pairAnalyzerMap.get(this.pair);
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

    protected void changeIgnoreSpread(boolean flag) {
        this.isIgnoreSpread = flag;
        log.info("ignore spread setting is set {}.", this.isIgnoreSpread);
    }

}
