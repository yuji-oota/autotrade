package autotrade.local.actor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
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

    private Rate latestRate;
    private List<Rate> rates;
    private int askThreshold;
    private int bidThreshold;
    private int middleThreshold;
    private BigDecimal countertradingRatio;
    private int ratioThresholdAsk;
    private int ratioThresholdBid;
    private Rate highWaterMark;
    private Rate lowWaterMark;
    private int countertradingAsk;
    private int countertradingBid;
    private boolean isSenceOfDirection;
    private int thresholdMinutes;

    public RateAnalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
        highWaterMark = Rate.builder().ask(Integer.MIN_VALUE).build();
        lowWaterMark = Rate.builder().bid(Integer.MAX_VALUE).build();
        countertradingRatio = AutoTradeProperties.getBigDecimal("autotrade.rateAnalizer.countertrading.ratio");
        thresholdMinutes = AutoTradeProperties.getInt("autotrade.rateAnalizer.threshold.minutes");
    }

    public void add(Rate rate) {
        latestRate = rate;
        if (rate.isDoubtful()) {
            log.info("doubtful rate is added {}", rate);
        } else {
            rates.add(rate);
            updateWaterMark(rate);
        }
        rates = rates.stream()
                .filter(r -> r.passed().toMillis() <= Duration.ofMinutes(20).toMillis())
                .collect(Collectors.toList());

        // 売買閾値設定
        Duration duration = Duration.ofMinutes(thresholdMinutes);
        askThreshold = maxWithin(duration);
        bidThreshold = minWithin(duration);
        middleThreshold = (askThreshold + bidThreshold) / 2;
        int ratioDiff = countertradingRatio.multiply(BigDecimal.valueOf((askThreshold - bidThreshold))).intValue();
        ratioThresholdAsk = askThreshold - ratioDiff;
        ratioThresholdBid = bidThreshold + ratioDiff;
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
                .mapToInt(r -> (r.getAsk() + r.getBid()) / 2)
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

    public boolean isUpward(Rate rate) {
        return averageWithin(Duration.ofMinutes(20)) < rate.getAsk();
    }
    public boolean isDownward(Rate rate) {
        return averageWithin(Duration.ofMinutes(20)) > rate.getBid();
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
        if (rates.size() > 100) {
            long count = IntStream.range(0, 10).filter(i -> {
                int index = new Random().nextInt(rates.size());
                Rate r = rates.get(index);
                return r.getAsk() == latestRate.getAsk() && r.getBid() == latestRate.getBid();
            }).count();
            if (count == 10) {
                return true;
            }
        }
        return false;
    }
    public void updateCountertradingAsk(int ask) {
        countertradingAsk = ask;
    }
    public void updateCountertradingBid(int bid) {
        countertradingBid = bid;
    }
    public void updateCountertrading(int ask, int bid) {
        updateCountertradingAsk(ask);
        updateCountertradingBid(bid);
    }
}
