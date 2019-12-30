package autotrade.local.actor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import autotrade.local.material.Rate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class RateAnalyzer {

    private List<Rate> rates;
    private int askThreshold;
    private int bidThreshold;
    private Rate highWaterMark;
    private Rate lowWaterMark;

    public RateAnalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
        highWaterMark = Rate.builder().ask(Integer.MIN_VALUE).build();
        lowWaterMark = Rate.builder().bid(Integer.MAX_VALUE).build();
    }

    public void add(Rate rate) {
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
        askThreshold = maxWithin(Duration.ofMinutes(10));
        bidThreshold = minWithin(Duration.ofMinutes(10));
        int range = askThreshold - bidThreshold;
        if (range < 40) {
            return;
        }
        int minutes = 5;
        if (40 <= range) {
            minutes = 5;
        }
        if (55 <= range) {
            minutes = 4;
        }
        if (70 <= range) {
            minutes = 3;
        }
        if (85 <= range) {
            minutes = 2;
        }
        if (100 <= range) {
            minutes = 1;
        }
        askThreshold = maxWithin(Duration.ofMinutes(minutes));
        bidThreshold = minWithin(Duration.ofMinutes(minutes));
    }

    public int rangeWithin(Duration duration) {
        return maxWithin(duration) - minWithin(duration);
    }

    public int maxWithin(Duration duration) {
        return rates.stream()
                .filter(r -> r.passed().toMillis() <= duration.toMillis())
                .map(Rate::getAsk)
                .max(Comparator.naturalOrder())
                .orElse(Integer.MAX_VALUE);
    }
    public int minWithin(Duration duration) {
        return rates.stream()
                .filter(r -> r.passed().toMillis() <= duration.toMillis())
                .map(Rate::getBid)
                .min(Comparator.naturalOrder())
                .orElse(Integer.MIN_VALUE);
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
        return r -> r.passed().toMillis() <= Duration.between(from, LocalDateTime.now()).toMillis()
                && Duration.between(to, LocalDateTime.now()).toMillis() <= r.passed().toMillis();
    }

    public boolean isUpward() {
        return minBetween(LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(10)) < bidThreshold;
    }
    public boolean isDownward() {
        return maxBetween(LocalDateTime.now().minusMinutes(20), LocalDateTime.now().minusMinutes(10)) > askThreshold;
    }

    public boolean isReachedAskThreshold(Rate rate) {
        return askThreshold <= rate.getAsk();
    }
    public boolean isReachedBidThreshold(Rate rate) {
        return rate.getBid() <= bidThreshold;
    }

}
