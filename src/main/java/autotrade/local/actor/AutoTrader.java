package autotrade.local.actor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.actor.MessageListener.ReservedMessage;
import autotrade.local.exception.ApplicationException;
import autotrade.local.material.AudioPath;
import autotrade.local.material.CurrencyPair;
import autotrade.local.material.DisplayMode;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import autotrade.local.material.StartMarginMode;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import autotrade.local.utility.WebDriverWrapper;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoTrader {

    private static AutoTrader instance;
    private static Path logFile;
    static {
        logFile = Paths.get("log", "autotrade-local.log");
    }

    private CurrencyPair pair;
    private CurrencyPair priorityPair;
    private Set<CurrencyPair> changeablePairs;
    private DisplayMode displayMode;

    private WebDriver driver;
    private WebDriverWrapper wrapper;
    private Map<CurrencyPair, RateAnalyzer> pairAnalyzerMap;
    private RateAnalyzer rateAnalyzer;
    private IndicatorManager indicatorManager;
    private UploadManager uploadManager;
    private LotManager lotManager;

    @SuppressWarnings("unused")
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

    private int targetAmountOneDay;
    private int startMargin;

    private LocalTime inactiveStart;
    private LocalTime inactiveEnd;

    private long lastFixed;

    private boolean isThroughOrder;
    private boolean isThroughFix;
    private boolean isIgnoreSpread;
    private boolean isAutoRecommended;
    private boolean isForceException;

    private AutoTrader() {
        pair = CurrencyPair.USDJPY;
        changeablePairs = AutoTradeProperties.getList("autotrade.order.pairs").stream()
                .map(CurrencyPair::valueOf)
                .collect(Collectors.toSet());
        displayMode = DisplayMode.CHART;

        targetAmountOneDay = AutoTradeProperties.getInt("autotrade.targetAmount.oneDay");
        inactiveStart = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.start")));
        inactiveEnd = LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse(AutoTradeProperties.get("autotrade.inactive.end")));

        pairAnalyzerMap = Stream.of(CurrencyPair.values()).collect(Collectors.toMap(pair -> pair, pair -> new RateAnalyzer()));
        rateAnalyzer = pairAnalyzerMap.get(pair);
        uploadManager = new UploadManager();
        lotManager = new LotManager();
        indicatorManager = new IndicatorManager();
        pubSubConnection = Messenger.createPubSubConnection(customizeMessageListener());

        isAutoRecommended = AutoTradeProperties.getBoolean("autotrade.autoRecommended.flag");
        if (CurrencyPair.getNames().contains(AutoTradeProperties.get("autotrade.autoRecommended.priorityPair"))) {
            priorityPair = CurrencyPair.valueOf(AutoTradeProperties.get("autotrade.autoRecommended.priorityPair"));
        }
    }

    public static AutoTrader getInstance() {
        if (Objects.isNull(instance)) {
            instance = new AutoTrader();
        }
        return instance;
    }

    public void operation() {

        try {
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

            // 通貨ペア設定
            wrapper.pairSettings();
            AutoTradeUtils.sleep(Duration.ofSeconds(1));

            // 通貨ペア変更
            wrapper.displayRateList();
            Stream.of(CurrencyPair.values()).forEach(p -> {
                Rate rate = buildRateFromList(p);
                lotManager.addSampleRateMap(p, rate);
                pairAnalyzerMap.get(p).add(rate);
            });
            Stream.of(CurrencyPair.values()).forEach(this::changePair);
            // ポジションが無ければUSD/JPYを設定
            changePair(CurrencyPair.USDJPY);

            // 開始時の証拠金を取得
            switch (StartMarginMode.valueOf(Messenger.get("startMarginMode"))) {
            case NEW:
                startMargin = AutoTradeUtils.toInt(wrapper.getMargin());
                break;
            default:
                startMargin = Integer.parseInt(Messenger.get("startMargin"));
                break;
            }
            Messenger.set("startMargin", String.valueOf(startMargin));
            Messenger.set("startMarginMode", StartMarginMode.CARRY_OVER.name());

            // Same引継ぎ
            Snapshot shapshot = buildSnapshot();
            if (shapshot.isPositionSame()) {
                changeThroughOrder(true);
                log.info("load Snapshot when samed to SameManager.");
                SameManager.setSnapshot(AutoTradeUtils.deserialize(Base64.getDecoder().decode(Messenger.get("snapshotWhenSamed"))));
            }
            // 反対売買閾値引継ぎ
            if (shapshot.hasPosition()) {
                log.info("load countertrading threshold when order to RateAnalyzer.");
                rateAnalyzer.loadCountertradingThreshold();
            }

            // 表示変更
            changeDisplay(displayMode);

            // 繰り返し実行
            while(true) {
                // 取引
                trade();

                // 取引後処理
                tradePostProcess();

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

    private Snapshot buildSnapshot() {
        return Snapshot.builder()
                .pair(wrapper.getPair())
                .askLot(AutoTradeUtils.toInt(wrapper.getAskLot()))
                .bidLot(AutoTradeUtils.toInt(wrapper.getBidLot()))
                .askAverageRate(AutoTradeUtils.toInt(wrapper.getAskAverageRate()))
                .bidAverageRate(AutoTradeUtils.toInt(wrapper.getBidAverageRate()))
                .askPipProfit(AutoTradeUtils.toInt(wrapper.getAskPipProfit()))
                .bidPipProfit(AutoTradeUtils.toInt(wrapper.getBidPipProfit()))
                .margin(AutoTradeUtils.toInt(wrapper.getMargin()))
                .todaysProfit(AutoTradeUtils.toInt(wrapper.getMargin()) - startMargin)
                .rate(buildRate())
                .build();
    }

    private Rate buildRate() {
        return Rate.builder()
                .ask(AutoTradeUtils.toInt(wrapper.getAskRate()))
                .bid(AutoTradeUtils.toInt(wrapper.getBidRate()))
                .timestamp(LocalDateTime.now())
                .build();
    }
    private Rate buildRateFromList(CurrencyPair pair) {
        return Rate.builder()
                .ask(AutoTradeUtils.toInt(wrapper.getAskRateFromList(pair)))
                .bid(AutoTradeUtils.toInt(wrapper.getBidRateFromList(pair)))
                .timestamp(LocalDateTime.now())
                .build();
    }

    private boolean hasPosition() {
        return AutoTradeUtils.toInt(wrapper.getAskLot()) > 0 || AutoTradeUtils.toInt(wrapper.getBidLot()) > 0;
    }

    private void trade() {

        Map<CurrencyPair, Rate> pairRateMap = new HashMap<>();

        // 最新情報取得
        Snapshot snapshot = buildSnapshot();

        // ペア別レート取得
        pairRateMap.put(pair, snapshot.getRate());
        if (displayMode == DisplayMode.RATELIST) {
            changeablePairs.stream()
            .filter(p -> p != pair)
            .filter(p -> Duration.between(pairAnalyzerMap.get(p).getLatestRate().getTimestamp(), LocalDateTime.now()).toSeconds() > 10)
            .forEach(p -> pairRateMap.put(p, buildRateFromList(p)));
        }

        // 確定見送り判定
        if (!isThroughFix) {
            // 最新情報を元に利益確定
            fix(snapshot);
        }

        if (isOrderable(snapshot)) {

            // 注文見送り判定
            if (!isThroughOrder) {
                // 最新情報を元に注文
                order(snapshot);
            }
        }

        // rateAnalyzerにレート追加
        pairRateMap.entrySet().stream().forEach(entry -> {
            pairAnalyzerMap.get(entry.getKey()).add(entry.getValue());
        });

    }

    private void tradePostProcess() {

        // 最新情報取得
        Snapshot snapshot = buildSnapshot();

        // SameManager初期化
        if (snapshot.isPositionSame()) {
            if (!SameManager.hasInstance()) {
                changeThroughOrder(true);
                changeAutoRecommended(false);
            }
            SameManager.setSnapshot(snapshot);
        }

        // 指標アラート
        if (indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(1))
                && !indicatorManager.isNextIndicatorWithin(Duration.ofSeconds(55))) {
            AutoTradeUtils.playAudioRandom(AudioPath.Alert);
        }

        // 非活性時間処理
        if (isInactiveTime()) {
            if (snapshot.isPositionNone()
                    && pair.getMinSpread() < snapshot.getRate().getSpread()) {
                // ポジションが無く、スプレッドが最小よりも広がった場合

                // 次回起動時設定
                Messenger.set("startMarginMode", StartMarginMode.NEW.name());

                // 非活性時間の終了までスリープする
                Duration durationToActive = Duration.between(LocalDateTime.now(), LocalDateTime.of(LocalDate.now(), inactiveEnd));
                log.info("application will sleep {} minutes, because of inactive time.", durationToActive.toMinutes());
                AutoTradeUtils.sleep(durationToActive);
            }
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

    }

    private void fix(Snapshot snapshot) {

        switch (snapshot.getStatus()) {
        case NONE:
            SameManager.close();
            break;
        case SAME:
            break;
        case ASK_SIDE:
        case BID_SIDE:

            // Sameポジション発生後の利益確定判定
            if (SameManager.hasInstance()) {

                // Sameポジション回復中の場合
                if (SameManager.getInstance().isRecovered(snapshot)) {
                    // Sameポジション回復達成で利益確定
                    fixAll(snapshot);
                    // 注文再開
                    changeThroughOrder(false);
                    changeAutoRecommended(true);
                }
                return;
            }

            if (pair == priorityPair) {
                // 優先通貨ペアの場合
                if (isFix(snapshot, lotManager.getInitial() * 5)) {
                    // 目標金額達成で利益確定
                    log.info("achieved target amount.");
                    fixAll(snapshot);
                    return;
                }
            } else {
                // その他通貨ペアの場合
                if (snapshot.getPositionProfit() >= lotManager.getInitial() * 5) {
                    // 目標金額達成で利益確定
                    log.info("achieved target amount.");
                    fixAll(snapshot);
                    return;
                }
            }
//            if (snapshot.hasBothSide()
//                    && isFix(snapshot, 0)) {
            if (snapshot.hasBothSide()
                    && snapshot.getPositionProfit() >= 0) {
                // 反対売買の場合
                // 目標金額達成で利益確定
                log.info("achieved countertrading.");
                fixAll(snapshot);
                return;
            }
            break;
        default:
        }
    }

    private boolean isFix(Snapshot snapshot, int targetAmount) {
        if (snapshot.getPositionProfit() >= targetAmount) {
            if (snapshot.isPositionAskSide()) {
                return rateAnalyzer.isReachedBidThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
            }
            if (snapshot.isPositionBidSide()) {
                return rateAnalyzer.isReachedAskThresholdWithin(snapshot.getRate(), Duration.ofMinutes(1));
            }
        }
        return false;
    }

    private boolean isOrderable(Snapshot snapshot) {
        if (Duration.between(rateAnalyzer.getEarliestRate().getTimestamp(), LocalDateTime.now()).toMinutes() < 5) {
            // 過去Rateがある程度存在しない場合は注文しない
            return false;
        }
        if (snapshot.getRate().isDoubtful()) {
            // スプレッドが開きすぎの場合は注文しない
            return false;
        }

        switch (snapshot.getStatus()) {
        case NONE:
        case SAME:
            if (System.currentTimeMillis() - lastFixed < Duration.ofSeconds(10).toMillis()) {
                // 利益確定から一定時間内の場合は注文しない
                return false;
            }
            if (rateAnalyzer.rangeWithin(Duration.ofMinutes(10)) < 50) {
                // 閾値間隔が狭い場合は注文しない
                return false;
            }
            if (indicatorManager.isNextIndicatorWithin(Duration.ofMinutes(5)) || indicatorManager.isPrevIndicatorWithin(Duration.ofSeconds(15))) {
                // 指標が近い場合は注文しない
                return false;
            }
            if (!isIgnoreSpread && pair.getMinSpread() < snapshot.getRate().getSpread()) {
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
        return true;
    }

    private void order(Snapshot snapshot) {

        Rate rate = snapshot.getRate();
        if (snapshot.getTotalProfit() > targetAmountOneDay) {
            // 一日の目標金額を達成した場合は消極的に取引する
            lotManager.modeNegative();
        }

        switch (snapshot.getStatus()) {
        case NONE:
            // ポジションがない場合
            if (rateAnalyzer.isReachedAskThreshold(rate)) {
                orderAsk(snapshot);
                rateAnalyzer.saveCountertradingThreshold(
                        rateAnalyzer.getAskThreshold(),
                        rateAnalyzer.getMiddleThreshold());
            }
            if (rateAnalyzer.isReachedBidThreshold(rate)) {
                orderBid(snapshot);
                rateAnalyzer.saveCountertradingThreshold(
                        rateAnalyzer.getMiddleThreshold(),
                        rateAnalyzer.getBidThreshold());
            }
            break;
        case ASK_SIDE:
            // 買いポジションが多い場合
            if (!SameManager.hasInstance()
                    && rateAnalyzer.isReachedCountertradingBid(rate)) {
                // 下値閾値を超えた場合
                // 逆ポジション取得
                orderBid(snapshot);
            }
            break;
        case BID_SIDE:
            // 売りポジションが多い場合
            if (!SameManager.hasInstance()
                    && rateAnalyzer.isReachedCountertradingAsk(rate)) {
                // 上値閾値を超えた場合
                // 逆ポジション取得
                orderAsk(snapshot);
            }
            break;
        case SAME:
            // ポジションが同数の場合
            break;
        default:
        }

    }

    private void forceSame() {
        Snapshot snapshot = buildSnapshot();
        int askLot = snapshot.getAskLot();
        int bidLot = snapshot.getBidLot();
        if (bidLot < askLot) {
            orderBid(askLot - bidLot);
        }
        if (askLot < bidLot) {
            orderAsk(bidLot - askLot);
        }
    }

    private boolean isInactiveTime() {
        return inactiveStart.isBefore(LocalTime.now()) && LocalTime.now().isBefore(inactiveEnd);
    }

    private void orderAsk(Snapshot snapshot) {
        int lot = lotManager.nextLot(snapshot);
        orderAsk(lot);
        log.info("order ask. lot {}", lot);
        AutoTradeUtils.printObject(snapshot);
        AutoTradeUtils.playAudioRandom(AudioPath.OrderSoundEffect);
    }
    private void orderBid(Snapshot snapshot) {
        int lot = lotManager.nextLot(snapshot);
        orderBid(lot);
        log.info("order bid. lot {}", lot);
        AutoTradeUtils.printObject(snapshot);
        AutoTradeUtils.playAudioRandom(AudioPath.OrderSoundEffect);
    }
    private void orderAsk(int lot) {
        int beforeLot = AutoTradeUtils.toInt(wrapper.getAskLot());
        wrapper.setLot(lot);
        wrapper.orderAsk();
        verifyOrder(beforeLot + lot, Snapshot::getAskLot);
    }
    private void orderBid(int lot) {
        int beforeLot = AutoTradeUtils.toInt(wrapper.getBidLot());
        wrapper.setLot(lot);
        wrapper.orderBid();
        verifyOrder(beforeLot + lot, Snapshot::getBidLot);
    }
    private void fixAll(Snapshot snapshot) {
        wrapper.fixAll();
        AutoTradeUtils.printObject(snapshot);
        AutoTradeUtils.playAudioRandom(AudioPath.FixSoundEffect);
        lastFixed = System.currentTimeMillis();
        // ベリファイ
        verifyOrder(0, Snapshot::getAskLot);
        verifyOrder(0, Snapshot::getBidLot);
    }
    private void verifyOrder(int lot, ToIntFunction<Snapshot> lotAfterOrder) {
        long verifyStarted = System.currentTimeMillis();
        while (true) {
            AutoTradeUtils.sleep(Duration.ofMillis(500));
            Snapshot snapshot = buildSnapshot();
            rateAnalyzer.add(snapshot.getRate());
            if (lot == lotAfterOrder.applyAsInt(snapshot)) {
                break;
            }
            if (System.currentTimeMillis() - verifyStarted > Duration.ofSeconds(10).toMillis()) {
                throw new ApplicationException("verify is failed.");
            }
        }
    }
    private void changeDisplay(DisplayMode displayMode) {
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
    private void changePair(CurrencyPair pair) {
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
        this.lotManager.changeInitialLot(pair);
        log.info("currency pair is changed to {}.", this.pair.getDescription());
    }
    private void changeRecommended() {
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

    private void changeThroughOrder(boolean flag) {
        this.isThroughOrder = flag;
        log.info("through order setting is set {}.", this.isThroughOrder);
    }
    private void changeThroughFix(boolean flag) {
        this.isThroughFix = flag;
        log.info("through fix setting is set {}.", this.isThroughFix);
    }
    private void changeIgnoreSpread(boolean flag) {
        this.isIgnoreSpread = flag;
        log.info("ignore spread setting is set {}.", this.isIgnoreSpread);
    }
    private void changeAutoRecommended(boolean flag) {
        this.isAutoRecommended = flag;
        log.info("auto recommended setting is set {}.", this.isAutoRecommended);
    }
    private void addChangeablePair(CurrencyPair pair) {
        changeablePairs.add(pair);
        log.info("{} ia added to changeable pair.", pair.getDescription());
    }
    private void removeChangeablePair(CurrencyPair pair) {
        changeablePairs.remove(pair);
        log.info("{} ia removed from changeable pair.", pair.getDescription());
    }

    private MessageListener customizeMessageListener() {
        return new MessageListener()
                .putCommand(ReservedMessage.SNAPSHOT, (args) -> Messenger.set(ReservedMessage.SNAPSHOT.name(), AutoTradeUtils.toJson(buildSnapshot())))
                .putCommand(ReservedMessage.UPLOADLOG, (args) -> uploadManager.upload(logFile))
                .putCommand(ReservedMessage.AUTOTRADELOG, (args) -> {
                    int logRows = 30;
                    if (args.length > 0) {
                        logRows = Integer.parseInt(args[0]);
                    }
                    List<String> lines = new ArrayList<>();
                    try {
                        lines = Files.readAllLines(logFile);
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
                .putCommand(ReservedMessage.FORCESAME, (args) -> this.forceSame())
                .putCommand(ReservedMessage.FORCEASK, (args) -> this.orderAsk(this.buildSnapshot()))
                .putCommand(ReservedMessage.FORCEBID, (args) -> this.orderBid(this.buildSnapshot()))
                .putCommand(ReservedMessage.LOTNEGATIVE, (args) -> lotManager.modeNegative())
                .putCommand(ReservedMessage.LOTPOSITIVE, (args) -> lotManager.modePositive())
                .putCommand(ReservedMessage.LOTPOSITIVEINCREMENT, (args) -> lotManager.incrementInitialPositive())
                .putCommand(ReservedMessage.LOTPOSITIVEDECREMENT, (args) -> lotManager.decrementInitialPositive())
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
                .putCommand(ReservedMessage.SAVECOUNTERTRADINGTHRESHOLD, (args) -> rateAnalyzer.saveCountertradingThreshold(rateAnalyzer.getAskThreshold(), rateAnalyzer.getBidThreshold()))
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
                ;
    }

}
