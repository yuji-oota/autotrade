package autotrade.local.actor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import autotrade.local.material.Rate;
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
    private Rate highWaterMark;
    private Rate lowWaterMark;
    private int countertradingAsk;
    private int countertradingBid;
    private boolean isSenceOfDirection;

    public RateAnalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
        highWaterMark = Rate.builder().ask(Integer.MIN_VALUE).build();
        lowWaterMark = Rate.builder().bid(Integer.MAX_VALUE).build();
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
        Duration duration = Duration.ofMinutes(10);
        askThreshold = maxWithin(duration);
        bidThreshold = minWithin(duration);
        middleThreshold = (askThreshold + bidThreshold) / 2;
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
    public void saveCountertradingThreshold(int ask, int bid) {
        countertradingAsk = ask;
        countertradingBid = bid;
        Messenger.set("countertradingAsk", String.valueOf(countertradingAsk));
        Messenger.set("countertradingBid", String.valueOf(countertradingBid));
    }
    public void loadCountertradingThreshold() {
        countertradingAsk = Integer.parseInt(Messenger.get("countertradingAsk"));
        countertradingBid = Integer.parseInt(Messenger.get("countertradingBid"));
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
    public void saveIsSenceOfDirection() {
        // 直近10分の1分間隔にmiddleThresholdが何回通過しているかカウントする
        long count = IntStream.range(0, 10).filter(i -> {
            LocalDateTime now = LocalDateTime.now();
            int max = maxBetween(now.minus(Duration.ofMinutes(i + 1)), now.minus(Duration.ofMinutes(i)));
            int min = minBetween(now.minus(Duration.ofMinutes(i + 1)), now.minus(Duration.ofMinutes(i)));
            if (min <= middleThreshold && middleThreshold <= max) {
                return true;
            }
            return false;
        }).count();

        isSenceOfDirection = true;
        if (count > 2) {
            // 何度も通過している場合は方向感無しとみなす
            isSenceOfDirection = false;
        }
        log.info("isSenceOfDirection {}, internal count {}", isSenceOfDirection, count);
    }
}
