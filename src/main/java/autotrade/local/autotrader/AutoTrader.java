package autotrade.local.autotrader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.actor.IndicatorManager;
import autotrade.local.actor.MessageListener;
import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.actor.Messenger;
import autotrade.local.actor.RateAnalyzer;
import autotrade.local.actor.ReserveManager;
import autotrade.local.actor.SameManager;
import autotrade.local.actor.UploadManager;
import autotrade.local.exception.ApplicationException;
import autotrade.local.material.AudioPath;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import autotrade.local.utility.WebDriverWrapper;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AutoTrader {

    protected CurrencyPair pair;
    protected CurrencyPair priorityPair;
    protected Set<CurrencyPair> changeablePairs;
    protected DisplayMode displayMode;

    protected WebDriver driver;
    protected WebDriverWrapper wrapper;
    protected Map<CurrencyPair, RateAnalyzer> pairAnalyzerMap;
    protected RateAnalyzer rateAnalyzer;
    protected IndicatorManager indicatorManager;
    protected UploadManager uploadManager;
//    protected LotManager lotManager;
    protected ReserveManager reserveManager;

    @SuppressWarnings("unused")
    protected StatefulRedisPubSubConnection<String, String> pubSubConnection;

    protected int startMargin;

    protected LocalTime inactiveStart;
    protected LocalTime inactiveEnd;

    protected long lastFixed;

    protected boolean isThroughOrder;
    protected boolean isThroughFix;
    protected boolean isIgnoreSpread;
    protected boolean isAutoRecommended;
    protected boolean isForceException;

    public AutoTrader() {
        pair = CurrencyPair.USDJPY;
        changeablePairs = AutoTradeProperties.getList("autotrade.order.pairs").stream()
                .map(CurrencyPair::valueOf)
                .collect(Collectors.toSet());
        displayMode = DisplayMode.CHART;

        inactiveStart = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));

        pairAnalyzerMap = Stream.of(CurrencyPair.values()).collect(Collectors.toMap(pair -> pair, pair -> new RateAnalyzer()));
        rateAnalyzer = pairAnalyzerMap.get(pair);
        uploadManager = new UploadManager();
//        lotManager = new LotManager();
        indicatorManager = new IndicatorManager();
        reserveManager = new ReserveManager();
        pubSubConnection = Messenger.createPubSubConnection(customizeMessageListener());

        isAutoRecommended = AutoTradeProperties.getBoolean("autotrade.autoRecommended.flag");
        if (CurrencyPair.getNames().contains(AutoTradeProperties.get("autotrade.autoRecommended.priorityPair"))) {
            priorityPair = CurrencyPair.valueOf(AutoTradeProperties.get("autotrade.autoRecommended.priorityPair"));
        }
    }

    public void operation() {

        try {
            // 初期処理
            initialize();

            // 繰り返し実行
            while(true) {

                // 最新情報取得
                Snapshot snapshot = buildSnapshot();

                // 取引前処理
                tradePreProcess(snapshot);

                // 取引
                trade(snapshot);

                // 取引後処理
                tradePostProcess(snapshot);

                // メッセージダイアログクローズ
                wrapper.cancelMessage();

                // 強制例外スロー
                if (isForceException) {
                    isForceException = false;
                    throw new ApplicationException("force exception occurred.");
                }

            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            driver.quit();
        }

    }

    abstract protected void order(Snapshot snapshot);
    abstract protected void fix(Snapshot snapshot);

    protected void initialize() {
        // WebDriver初期化
        driver = new ChromeDriver();
        wrapper = new WebDriverWrapper(driver);

        // 指標を確認する
        if (!indicatorManager.hasIndicator()) {
            // 本日分
            indicatorManager.addIndicators(wrapper.getIndicators(LocalDate.now()));
            // 翌日分
            indicatorManager.addIndicators(wrapper.getIndicators(LocalDate.now().plusDays(1)));
            log.info("indicators is got.");
            AutoTradeUtils.printObject(indicatorManager.getIndicators());
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

        // 通貨ペア変更
        wrapper.pairSettings();
        wrapper.displayRateList();
        Stream.of(CurrencyPair.values()).forEach(this::changePair);
        changePair(CurrencyPair.valueOf(AutoTradeProperties.get("autotrade.order.pair")));

        // 表示変更
        changeDisplay(displayMode);

    }

    protected Snapshot buildSnapshot() {
        return Snapshot.builder()
                .pair(CurrencyPair.valueOf(wrapper.getPair().replace("/", "")))
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
        int ask = 0;
        int bid = 0;
        try {
            ask = AutoTradeUtils.toInt(futureAsk.get());
            bid = AutoTradeUtils.toInt(futureBid.get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return Rate.builder()
                .pair(CurrencyPair.valueOf(wrapper.getPair().replace("/", "")))
                .ask(ask)
                .bid(bid)
                .timestamp(LocalDateTime.now())
                .build();
    }
    protected Rate buildRateFromList(CurrencyPair pair) {
        return Rate.builder()
                .pair(pair)
                .ask(AutoTradeUtils.toInt(wrapper.getAskRateFromList(pair)))
                .bid(AutoTradeUtils.toInt(wrapper.getBidRateFromList(pair)))
                .timestamp(LocalDateTime.now())
                .build();
    }

    protected boolean hasPosition() {
        return AutoTradeUtils.toInt(wrapper.getAskLot()) > 0 || AutoTradeUtils.toInt(wrapper.getBidLot()) > 0;
    }

    protected Rate buildLastDayBeforeRate() {
        String theDayBeforeDiff = driver.findElement(By.xpath("//*[@id=\"hl-div\"]/span[5]")).getText();
        Rate lastDayBeforeRate = Rate.builder().pair(pair).ask(0).bid(0).timestamp(LocalDateTime.now()).build();
        int lastDayBeforeBid = AutoTradeUtils.toInt(theDayBeforeDiff.substring(1));
        if ("▼".equals(theDayBeforeDiff.substring(0, 1))) {
            lastDayBeforeBid = lastDayBeforeBid * -1;
        }
        Snapshot snapshot = buildSnapshot();
        lastDayBeforeRate.setBid(snapshot.getRate().getBid() - lastDayBeforeBid);
        lastDayBeforeRate.setAsk(lastDayBeforeRate.getBid() + pair.getMinSpread());
        return lastDayBeforeRate;
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

    protected void preOrder(Snapshot snapshot) {}
    protected void postOrder(Snapshot snapshot) {}
    protected void preFix(Snapshot snapshot) {}
    protected void postFix(Snapshot snapshot) {}

    protected void tradePreProcess(Snapshot snapshot) {
        // ペア別レート取得
        Map<CurrencyPair, Rate> pairRateMap = new HashMap<>();
        pairRateMap.put(pair, snapshot.getRate());
        if (displayMode == DisplayMode.RATELIST
                && LocalDateTime.now().getSecond() % 10 == 0) {
            changeablePairs.stream()
            .filter(p -> p != pair)
            .forEach(p -> pairRateMap.put(p, buildRateFromList(p)));
        }
        // rateAnalyzerにレート追加
        pairRateMap.entrySet().stream().forEach(entry -> {
            pairAnalyzerMap.get(entry.getKey()).add(entry.getValue());
        });
    }

    protected void tradePostProcess(Snapshot snapshot) {

        LocalDateTime now = LocalDateTime.now();

        // 指標アラート
        if (indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(1))
                && !indicatorManager.isNextIndicatorWithin(Duration.ofSeconds(59))) {
            AutoTradeUtils.playAudioRandom(AudioPath.Alert);
            AutoTradeUtils.sleep(Duration.ofSeconds(1));
        }

        // 非活性時間処理
        if (isSleep(snapshot)) {

            // 非活性時間の終了までスリープする
            Duration durationToActive = Duration.between(now, LocalDateTime.of(LocalDate.now(), inactiveEnd));
            log.info("application will sleep {} minutes, because of inactive time.", durationToActive.toMinutes());
            AutoTradeUtils.sleep(durationToActive);
        }

        // 推奨通貨ペア自動選択
        if (isAutoRecommended && !snapshot.hasPosition()) {
            changeDisplay(DisplayMode.RATELIST);

            if (Objects.nonNull(priorityPair)
                    && pairAnalyzerMap.get(priorityPair).rangeWithin(Duration.ofMinutes(10)) >= 50) {
                // 優先通貨ペアが設定されている場合
                // 且つ閾値間隔が広い場合
                changePair(priorityPair);
            } else {
                changeRecommended();
            }
        }

        // お知らせ対策
        if (!isThroughOrder
                && rateAnalyzer.hasDoubtfulRates()) {
            throw new ApplicationException("RateAnalyzer has doubtful rates.");
        }

        // 5:00時点でクラウドセーブ
        if (snapshot.hasPosition()
                && now.getHour() == 5
                && now.getMinute() == 0
                && now.getSecond() < 1) {
            cloudSave();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));
        }

    }

    protected boolean isSleep(Snapshot snapshot) {
        return isInactiveTime()
                && snapshot.isPositionNone()
                && snapshot.isSpreadWiden();
    }

    protected boolean isFixable(Snapshot snapshot) {
        if (isThroughFix) {
            return false;
        }
        return true;
    }

    protected boolean isOrderable(Snapshot snapshot) {
        if (isThroughOrder) {
            return false;
        }
        if (Duration.between(rateAnalyzer.getEarliestRate().getTimestamp(), LocalDateTime.now()).toMinutes() < 1) {
            // 過去Rateがある程度存在しない場合は注文しない
            return false;
        }
        if (rateAnalyzer.isDoubtful()) {
            // 疑わしいRateの場合は注文しない
            return false;
        }
        if (!rateAnalyzer.isMoved()) {
            // レートが動いていない場合は注文しない
            return false;
        }

        switch (snapshot.getStatus()) {
        case NONE:
        case SAME:
            if (isCalm()) {
                // 閾値間隔が狭い場合は注文しない
                return false;
            }
            if (isNearIndicator()) {
                // 指標が近い場合は注文しない
                return false;
            }
            if (!isIgnoreSpread && snapshot.isSpreadWiden()) {
                // スプレッドを無視しない
                // 且つスプレッドが開いている場合は注文しない
                return false;
            }
            break;
        case ASK_SIDE:
        case BID_SIDE:
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

    protected boolean isNearIndicator() {
        return indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(5)) || indicatorManager.isPrevIndicatorWithin(Duration.ofSeconds(15));
    }

    protected void forceSame(Snapshot snapshot) {
        int askLot = snapshot.getAskLot();
        int bidLot = snapshot.getBidLot();
        if (bidLot < askLot) {
            orderBid(askLot - bidLot);
        }
        if (askLot < bidLot) {
            orderAsk(bidLot - askLot);
        }
    }

    protected boolean isInactiveTime() {
        return inactiveStart.isBefore(LocalTime.now()) && LocalTime.now().isBefore(inactiveEnd);
    }

    protected boolean isCalm() {
        return rateAnalyzer.rangeWithin(Duration.ofMinutes(5)) < 25;
    }

//    protected void orderAsk(Snapshot snapshot) {
//        int lot = lotManager.nextLot(snapshot);
//        orderAsk(lot);
//        AutoTradeUtils.printObject(snapshot);
//    }
//    protected void orderBid(Snapshot snapshot) {
//        int lot = lotManager.nextLot(snapshot);
//        orderBid(lot);
//        AutoTradeUtils.printObject(snapshot);
//    }
    protected void orderAsk(int lot) {
        String rate = wrapper.getAskRate();
        int beforeLot = AutoTradeUtils.toInt(wrapper.getAskLot());
        wrapper.setLot(lot);
        wrapper.orderAsk();
        verifyOrder(beforeLot + lot, Snapshot::getAskLot);
        log.info("order ask. lot {}, rate {}", lot, rate);
    }
    protected void orderBid(int lot) {
        String rate = wrapper.getBidRate();
        int beforeLot = AutoTradeUtils.toInt(wrapper.getBidLot());
        wrapper.setLot(lot);
        wrapper.orderBid();
        verifyOrder(beforeLot + lot, Snapshot::getBidLot);
        log.info("order bid. lot {}, rate {}", lot, rate);
    }
    protected void fixAll(Snapshot snapshot) {
        wrapper.fixAll();
        AutoTradeUtils.playAudioRandom(AudioPath.FixSoundEffect);
        verifyOrder(0, Snapshot::getAskLot);
        verifyOrder(0, Snapshot::getBidLot);
        lastFixed = System.currentTimeMillis();
        log.info("fix all position.");
        AutoTradeUtils.printObject(snapshot);
    }
    protected void fixAsk(Snapshot snapshot) {
        wrapper.fixAsk();
        verifyOrder(0, Snapshot::getAskLot);
        log.info("fix ask position.");
        AutoTradeUtils.printObject(snapshot);
    }
    protected void fixBid(Snapshot snapshot) {
        wrapper.fixBid();
        verifyOrder(0, Snapshot::getBidLot);
        log.info("fix bid position.");
        AutoTradeUtils.printObject(snapshot);
    }
    protected void verifyOrder(int lot, ToIntFunction<Snapshot> lotAfterOrder) {
        long verifyStarted = System.currentTimeMillis();
        while (true) {
            AutoTradeUtils.sleep(Duration.ofMillis(500));
            Snapshot snapshot = buildSnapshot();
            if (lot == lotAfterOrder.applyAsInt(snapshot)) {
                break;
            }
            if (System.currentTimeMillis() - verifyStarted > Duration.ofSeconds(10).toMillis()) {
                throw new ApplicationException("verify is failed.");
            }
        }
    }
    protected void changeDisplay(DisplayMode displayMode) {
        this.displayMode = displayMode;
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
        if (!this.changeablePairs.contains(pair)) {
            log.info("currency pair {} is not changeable.", pair.name());
            return;
        }
        if (this.hasPosition()) {
            log.info("currency pair {} is not changed because of position exists.", pair.name());
            return;
        }
        this.pair = pair;
        wrapper.displayRateList();
        wrapper.changePair(this.pair.getDescription());
        this.changeDisplay(this.displayMode);
        this.rateAnalyzer = this.pairAnalyzerMap.get(this.pair);
//        this.lotManager.changePair(pair, AutoTradeUtils.toInt(wrapper.getEffectiveMargin()));
        log.info("currency pair is changed to {}.", this.pair.getDescription());
    }
    protected void changeRecommended() {
        if (this.displayMode != DisplayMode.RATELIST) {
            log.info("change recommended is executable when display mode RATELIST.");
            return;
        }
        CurrencyPair recommended = this.pairAnalyzerMap.entrySet().stream()
                .filter(entry -> this.changeablePairs.contains(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getValue().rangeWithin(Duration.ofMinutes(10))))
                .get()
                .getKey();
        this.changePair(recommended);
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
    protected void changeAutoRecommended(boolean flag) {
        this.isAutoRecommended = flag;
        log.info("auto recommended setting is set {}.", this.isAutoRecommended);
    }
    protected void addChangeablePair(CurrencyPair pair) {
        changeablePairs.add(pair);
        log.info("{} ia added to changeable pair.", pair.getDescription());
    }
    protected void removeChangeablePair(CurrencyPair pair) {
        changeablePairs.remove(pair);
        log.info("{} ia removed from changeable pair.", pair.getDescription());
    }
    protected void loadSameSnapshot() {
        SameManager.setSnapshot(AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("snapshotWhenSamed"))));
        log.info("loaded Snapshot when samed to SameManager.");
    }
    protected void cloudSave() {
        Messenger.set("currencyPair", pair.name());
        log.info("saved currency pair {}.", pair.name());

        Messenger.set("countertradingAsk", String.valueOf(rateAnalyzer.getCountertradingAsk()));
        Messenger.set("countertradingBid", String.valueOf(rateAnalyzer.getCountertradingBid()));
        log.info("saved countertrading threshold ask {} bid {}.", rateAnalyzer.getCountertradingAsk(), rateAnalyzer.getCountertradingBid());
    }
    protected void cloudLoad() {
        CurrencyPair currencyPair = CurrencyPair.valueOf(Messenger.get("currencyPair"));
        log.info("loaded currency pair {}.", currencyPair);

        pairAnalyzerMap.get(currencyPair).updateCountertrading(
                Integer.parseInt(Messenger.get("countertradingAsk")),
                Integer.parseInt(Messenger.get("countertradingBid")));
        log.info("loaded countertrading threshold ask {} bid {} to RateAnalyzer.", pairAnalyzerMap.get(currencyPair).getCountertradingAsk(), pairAnalyzerMap.get(currencyPair).getCountertradingBid());
    }
//    protected void resetSame() {
//        Snapshot snapshot = buildSnapshot();
//        fixAll(snapshot);
//        orderAsk(lotManager.getLimit());
//        orderBid(lotManager.getLimit());
//    }

    protected MessageListener customizeMessageListener() {
        return new MessageListener()
                .putCommand(ReservedMessage.SNAPSHOT, (args) -> Messenger.set(ReservedMessage.SNAPSHOT.name(), AutoTradeUtils.toJson(buildSnapshot())))
                .putCommand(ReservedMessage.UPLOADLOG, (args) -> uploadManager.upload(Paths.get("log", "autotrade-local.log")))
                .putCommand(ReservedMessage.AUTOTRADELOG, (args) -> {
                    int logRows = 30;
                    if (args.length > 0) {
                        logRows = Integer.parseInt(args[0]);
                    }
                    List<String> lines = new ArrayList<>();
                    try {
                        lines = Files.readAllLines(Paths.get("log", "autotrade-local.log"));
                    } catch (IOException e) {
                        throw new ApplicationException(e);
                    }
                    if (args.length > 1) {
                        lines = lines.stream().filter(s -> s.contains(args[1])).collect(Collectors.toList());
                    }
                    Messenger.set(ReservedMessage.AUTOTRADELOG.name(),
                            lines.subList(Math.max(0, lines.size() - logRows), lines.size()).stream().collect(Collectors.joining("\n")));
                })
                .putCommand(ReservedMessage.FIXASK, (args) -> wrapper.fixAsk())
                .putCommand(ReservedMessage.FIXBID, (args) -> wrapper.fixBid())
                .putCommand(ReservedMessage.FIXALL, (args) -> wrapper.fixAll())
                .putCommand(ReservedMessage.FORCESAME, (args) -> this.forceSame(this.buildSnapshot()))
//                .putCommand(ReservedMessage.FORCEASK, (args) -> this.orderAsk(this.buildSnapshot()))
//                .putCommand(ReservedMessage.FORCEBID, (args) -> this.orderBid(this.buildSnapshot()))
//                .putCommand(ReservedMessage.LOTNEGATIVE, (args) -> lotManager.modeNegative())
//                .putCommand(ReservedMessage.LOTPOSITIVE, (args) -> lotManager.modePositive())
//                .putCommand(ReservedMessage.LOTPOSITIVEINCREMENT, (args) -> lotManager.incrementInitialPositive())
//                .putCommand(ReservedMessage.LOTPOSITIVEDECREMENT, (args) -> lotManager.decrementInitialPositive())
                .putCommand(ReservedMessage.THROUGHORDER, (args) -> {
                    if (args.length > 0) {
                        this.changeThroughOrder(Boolean.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.THROUGHFIX, (args) -> {
                    if (args.length > 0) {
                        this.changeThroughFix(Boolean.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.IGNORESPREAD, (args) -> {
                    if (args.length > 0) {
                        this.changeIgnoreSpread(Boolean.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.AUTORECOMMENDED, (args) -> {
                    if (args.length > 0) {
                        this.changeAutoRecommended(Boolean.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.SAVECOUNTERTRADINGTHRESHOLD, (args) -> rateAnalyzer.updateCountertrading(rateAnalyzer.getAskThreshold(), rateAnalyzer.getBidThreshold()))
                .putCommand(ReservedMessage.CHANGEPAIR, (args) -> {
                    if (args.length > 0) {
                        this.changePair(CurrencyPair.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.CHANGERECOMMENDED, (args) -> this.changeRecommended())
                .putCommand(ReservedMessage.DISPLAYCHART, (args) -> this.changeDisplay(DisplayMode.CHART))
                .putCommand(ReservedMessage.DISPLAYRATELIST, (args) -> this.changeDisplay(DisplayMode.RATELIST))
                .putCommand(ReservedMessage.FORCEEXCEPTION, (args) -> this.isForceException = true)
                .putCommand(ReservedMessage.CHANGEABLEPAIRADD, (args) -> {
                    if (args.length > 0) {
                        this.addChangeablePair(CurrencyPair.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.CHANGEABLEPAIRREMOVE, (args) -> {
                    if (args.length > 0) {
                        this.removeChangeablePair(CurrencyPair.valueOf(args[0]));
                    }
                })
                .putCommand(ReservedMessage.LOADSAMESNAPSHOT, (args) -> this.loadSameSnapshot())
                .putCommand(ReservedMessage.RESERVELIMITFIXASK, (args) -> {
                    if (args.length > 0) {
                        if (AutoTradeUtils.isInt(args[0])) {
                            reserveManager.reserveLimitFixAsk(Integer.parseInt(args[0]));
                        }
                    }
                })
                .putCommand(ReservedMessage.RESERVELIMITFIXBID, (args) -> {
                    if (args.length > 0) {
                        if (AutoTradeUtils.isInt(args[0])) {
                            reserveManager.reserveLimitFixBid(Integer.parseInt(args[0]));
                        }
                    }
                })
                .putCommand(ReservedMessage.RESERVESTOPFIXASK, (args) -> {
                    if (args.length > 0) {
                        if (AutoTradeUtils.isInt(args[0])) {
                            reserveManager.reserveStopFixAsk(Integer.parseInt(args[0]));
                        }
                    }
                })
                .putCommand(ReservedMessage.RESERVESTOPFIXBID, (args) -> {
                    if (args.length > 0) {
                        if (AutoTradeUtils.isInt(args[0])) {
                            reserveManager.reserveStopFixBid(Integer.parseInt(args[0]));
                        }
                    }
                })
                .putCommand(ReservedMessage.CLOUDSAVE, (args) -> this.cloudSave())
                .putCommand(ReservedMessage.CLOUDLOAD, (args) -> this.cloudLoad())
//                .putCommand(ReservedMessage.RESETSAME, (args) -> this.resetSame())
                ;
    }

}
