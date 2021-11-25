package autotrade.local.actor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import autotrade.local.material.Rate;
import autotrade.local.utility.AutoTradeProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class RateAnalyzer {

    private List<Rate> rates;
    private int askThreshold;
    private int bidThreshold;
    private int middleThreshold;
    private Rate highWaterMark;
    private Rate lowWaterMark;
    private int countertradingAsk;
    private int countertradingBid;
    private boolean isSenceOfDirection;
    private Duration thresholdDuration;
    private boolean isMoved;
    private int noMoveCounter;
    private ArrayDeque<Rate> latestRateQueue;
    private ArrayDeque<Rate> diffRateQueue;
    private Duration calmDration;
    private int calmRange;

    public RateAnalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
        highWaterMark = Rate.builder().ask(Integer.MIN_VALUE).build();
        lowWaterMark = Rate.builder().bid(Integer.MAX_VALUE).build();
        thresholdDuration = Duration.ofSeconds(AutoTradeProperties.getInt("autotrade.rateAnalizer.threshold.seconds"));
        latestRateQueue = new ArrayDeque<Rate>();
        latestRateQueue.add(Rate.builder().ask(Integer.MIN_VALUE).bid(Integer.MAX_VALUE).build());
        diffRateQueue = new ArrayDeque<Rate>();
        diffRateQueue.add(Rate.builder().ask(Integer.MIN_VALUE).bid(Integer.MAX_VALUE).build());
        calmDration = Duration.ofSeconds(AutoTradeProperties.getInt("autotrade.rateAnalizer.calm.seconds"));
        calmRange = AutoTradeProperties.getInt("autotrade.rateAnalizer.calm.range");
    }

    public void add(Rate rate) {

        Rate latestRate = latestRateQueue.getLast();
        latestRateQueue.add(rate);
        if (latestRateQueue.size() > 5) {
            latestRateQueue.poll();
        }
        diffRateQueue.add(rate);
        if (diffRateQueue.size() > 2) {
            diffRateQueue.poll();
        }

        isMoved = false;
        noMoveCounter++;
        if (latestRate.getAsk() != rate.getAsk()
                || latestRate.getBid() != rate.getBid()) {
            isMoved = true;
            noMoveCounter = 0;
        }

        if (!isDoubtful()) {
            rates.add(rate);
            updateWaterMark(rate);
        }
        rates = rates.stream()
                .filter(r -> r.passed().toMillis() <= Duration.ofMinutes(20).toMillis())
                .collect(Collectors.toList());

        // 売買閾値設定
        askThreshold = maxWithin(thresholdDuration);
        bidThreshold = minWithin(thresholdDuration);
        middleThreshold = (askThreshold + bidThreshold) / 2;
    }

    public int rangeThreshold() {
        return askThreshold - bidThreshold;
    }

    public int rangeWithin(Duration duration) {
        return maxWithin(duration) - minWithin(duration);
    }

    public int maxWithin(Duration duration) {
        return maxBetween(LocalDateTime.now().minus(duration), LocalDateTime.now());
    }

    public int minWithin(Duration duration) {
        return minBetween(LocalDateTime.now().minus(duration), LocalDateTime.now());
    }

    public int middleWithin(Duration duration) {
        return (maxWithin(duration) + minWithin(duration)) / 2;
    }

    public int maxBetween(Temporal from, Temporal to) {
        return rates.stream()
                .filter(rateBetweenFilter(from, to))
                .map(Rate::getAsk)
                .max(Comparator.naturalOrder())
                .orElse(Integer.MAX_VALUE);
    }

    public int minBetween(Temporal from, Temporal to) {
        return rates.stream()
                .filter(rateBetweenFilter(from, to))
                .map(Rate::getBid)
                .min(Comparator.naturalOrder())
                .orElse(Integer.MIN_VALUE);
    }

    public int averageWithin(Duration duration) {
        return averageBetween(LocalDateTime.now().minus(duration), LocalDateTime.now());
    }

    public int averageBetween(Temporal from, Temporal to) {
        return (int) rates.stream()
                .filter(rateBetweenFilter(from, to))
                .mapToInt(Rate::getMiddle)
                .average()
                .orElse(0.0);
    }

    public int halfWithin(Duration duration) {
        return rangeWithin(duration) / 2;
    }

    private void updateWaterMark(Rate rate) {
        if (highWaterMark.getAsk() < rate.getAsk()) {
            highWaterMark = rate;
        }
        if (rate.getBid() < lowWaterMark.getBid()) {
            lowWaterMark = rate;
        }
    }

    private Predicate<Rate> rateBetweenFilter(Temporal from, Temporal to) {
        return r -> !Duration.between(from, r.getTimestamp()).isNegative()
                && !Duration.between(r.getTimestamp(), to).isNegative();
    }

    public boolean isUpwardWithin(Duration duration) {
        LocalDateTime whenMax = rates.stream()
                .filter(rateBetweenFilter(LocalDateTime.now().minus(duration), LocalDateTime.now()))
                .max(Comparator.comparing(Rate::getAsk))
                .map(Rate::getTimestamp)
                .orElse(LocalDateTime.now());
        LocalDateTime whenMin = rates.stream()
                .filter(rateBetweenFilter(LocalDateTime.now().minus(duration), LocalDateTime.now()))
                .min(Comparator.comparing(Rate::getBid))
                .map(Rate::getTimestamp)
                .orElse(LocalDateTime.now());
        return whenMax.isAfter(whenMin);
    }

    public boolean isDownwardWithin(Duration duration) {
        return !isUpwardWithin(duration);
    }

    public boolean isReachedAskThreshold(Rate rate) {
        return askThreshold <= rate.getAsk();
    }

    public boolean isReachedBidThreshold(Rate rate) {
        return rate.getBid() <= bidThreshold;
    }

    public boolean isReachedAskThresholdWithin(Rate rate, Duration duration) {
        return maxWithin(duration) <= rate.getAsk();
    }

    public boolean isReachedBidThresholdWithin(Rate rate, Duration duration) {
        return rate.getBid() <= minWithin(duration);
    }

    public boolean isReachedCountertradingAsk(Rate rate) {
        return countertradingAsk <= rate.getAsk();
    }

    public boolean isReachedCountertradingBid(Rate rate) {
        return rate.getBid() <= countertradingBid;
    }

    public boolean isReachedAverageAsk(Rate rate) {
        return averageWithin(thresholdDuration) <= rate.getAsk();
    }

    public boolean isReachedAverageBid(Rate rate) {
        return averageWithin(thresholdDuration) >= rate.getBid();
    }

    public Rate getEarliestRate() {
        return rates.stream()
                .min(Comparator.comparing(Rate::getTimestamp))
                .orElse(Rate.builder().timestamp(LocalDateTime.now()).build());
    }

    public long passCountWithin(int threshold, int minutes) {
        return IntStream.range(0, minutes).filter(i -> {
            LocalDateTime now = LocalDateTime.now();
            int max = maxBetween(now.minus(Duration.ofMinutes(i + 1)), now.minus(Duration.ofMinutes(i)));
            int min = minBetween(now.minus(Duration.ofMinutes(i + 1)), now.minus(Duration.ofMinutes(i)));
            if (min <= threshold && threshold <= max) {
                return true;
            }
            return false;
        }).count();
    }

    public void saveIsSenceOfDirection() {
        // 直近10分の1分間隔にmiddleThresholdが何回通過しているかカウントする
        long count = passCountWithin(middleThreshold, 10);

        isSenceOfDirection = true;
        if (count > 2) {
            // 何度も通過している場合は方向感無しとみなす
            isSenceOfDirection = false;
        }
        log.info("isSenceOfDirection {}, internal count {}", isSenceOfDirection, count);
    }

    public boolean hasDoubtfulRates() {
        if (noMoveCounter > 1000) {
            return true;
        }
        return false;
    }

    public void setCountertradingAsk(int ask) {
        countertradingAsk = ask;
    }

    public void setCountertradingBid(int bid) {
        countertradingBid = bid;
    }

    public void updateCountertrading(int ask, int bid) {
        setCountertradingAsk(ask);
        setCountertradingBid(bid);
    }

    public void setThresholdDuration(Duration duration) {
        thresholdDuration = duration;
    }

    public void resetCountertrading() {
        countertradingAsk = Integer.MAX_VALUE;
        countertradingBid = Integer.MIN_VALUE;
    }

    public boolean isDoubtful() {
        Rate latestRate = latestRateQueue.getLast();
        if (latestRate.getAsk() == 0 || latestRate.getBid() == 0) {
            return true;
        }
        if (latestRate.getAsk() - latestRate.getBid() > 1000
                && latestRate.isNearThousand()) {
            return true;
        }
        if (latestRate.getAsk() < latestRate.getBid()) {
            return true;
        }
        if (latestRate.isSpreadWiden()) {
            if (latestRateQueue.stream().allMatch(Rate::isSpreadWiden)) {
                return false;
            }
            return true;
        }
        return false;
    }

    public boolean isAskUp() {
        return diffRateQueue.getFirst().getAsk() < diffRateQueue.getLast().getAsk();
    }

    public boolean isBidDown() {
        return diffRateQueue.getFirst().getBid() > diffRateQueue.getLast().getBid();
    }

    public boolean isCalm() {
        return rangeWithin(calmDration) < calmRange;
    }
}
