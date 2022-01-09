package autotrade.local.actor;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import autotrade.local.material.Rate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
@Getter
public class RateAnalyzer implements Serializable {

    private List<Rate> rates;
    private int askThreshold;
    private int bidThreshold;
    private int middleThreshold;
    private Rate highWaterMark;
    private Rate lowWaterMark;
    private int countertradingAsk;
    private int countertradingBid;
    private boolean isSenceOfDirection;
    private int noMoveCounter;
    private ArrayDeque<Rate> latestRateQueue;
    private ArrayDeque<Rate> diffRateQueue;

    @Value("#{T(java.time.Duration).ofSeconds('${autotrade.rateAnalizer.threshold.seconds}')}")
    private Duration thresholdDuration;
    @Value("#{T(java.time.Duration).ofSeconds('${autotrade.rateAnalizer.calm.seconds}')}")
    private Duration calmDration;
    @Value("${autotrade.rateAnalizer.calm.range}")
    private int calmRange;

    public RateAnalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
        highWaterMark = Rate.builder().ask(Integer.MIN_VALUE).build();
        lowWaterMark = Rate.builder().bid(Integer.MAX_VALUE).build();
        latestRateQueue = new ArrayDeque<Rate>();
        latestRateQueue.add(Rate.builder().ask(Integer.MIN_VALUE).bid(Integer.MAX_VALUE).build());
        diffRateQueue = new ArrayDeque<Rate>();
        diffRateQueue.add(Rate.builder().ask(Integer.MIN_VALUE).bid(Integer.MAX_VALUE).build());
    }

    public void add(Rate rate) {

        latestRateQueue.add(rate);
        if (latestRateQueue.size() > 5) {
            latestRateQueue.poll();
        }
        diffRateQueue.add(rate);
        if (diffRateQueue.size() > 2) {
            diffRateQueue.poll();
        }

        noMoveCounter++;
        if (isMoved()) {
            noMoveCounter = 0;
        }

        if (!isDoubtful()) {
            rates.add(rate);
            updateWaterMark(rate);
        }
        LocalDateTime from = LocalDateTime.now().minus(Duration.ofMinutes(10));
        rates = rates.stream()
                .filter(r -> r.getTimestamp().isAfter(from))
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

    public int maxBetween(LocalDateTime from, LocalDateTime to) {
        return rates.stream()
                .filter(rateBetweenFilter(from, to))
                .map(Rate::getAsk)
                .max(Comparator.naturalOrder())
                .orElse(Integer.MAX_VALUE);
    }

    public int minBetween(LocalDateTime from, LocalDateTime to) {
        return rates.stream()
                .filter(rateBetweenFilter(from, to))
                .map(Rate::getBid)
                .min(Comparator.naturalOrder())
                .orElse(Integer.MIN_VALUE);
    }

    public int averageWithin(Duration duration) {
        return averageBetween(LocalDateTime.now().minus(duration), LocalDateTime.now());
    }

    public int averageBetween(LocalDateTime from, LocalDateTime to) {
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

    private Predicate<Rate> rateBetweenFilter(LocalDateTime from, LocalDateTime to) {
        return r -> r.getTimestamp().isAfter(from) && r.getTimestamp().isBefore(to);
    }

    public boolean isUpwardWithin(Duration duration) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minus(duration);

        LocalDateTime maxDateTime = rates.stream()
                .filter(rateBetweenFilter(from, to))
                .max(Comparator.comparing(Rate::getAsk))
                .map(Rate::getTimestamp)
                .orElse(LocalDateTime.now());
        LocalDateTime minDateTime = rates.stream()
                .filter(rateBetweenFilter(from, to))
                .min(Comparator.comparing(Rate::getBid))
                .map(Rate::getTimestamp)
                .orElse(LocalDateTime.now());
        return maxDateTime.isAfter(minDateTime);
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
        if (latestRate.anyMatch(".*00.", ".*99.")) {
            int averageNear000 = (int) rates.stream()
                    .filter(r -> r.anyMatch(".*0..", ".*9.."))
                    .mapToInt(Rate::getMiddle)
                    .average()
                    .orElse(0.0);
            if (averageNear000 == 0) {
                return true;
            }
            if (Math.abs(averageNear000 - latestRate.getAsk()) > 250
                    || Math.abs(averageNear000 - latestRate.getBid()) > 250) {
                return true;
            }
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

    public boolean isMoved() {
        return diffRateQueue.getFirst().getAsk() != diffRateQueue.getLast().getAsk()
                || diffRateQueue.getFirst().getBid() != diffRateQueue.getLast().getBid();
    }

    public void filterNarrow() {
        rates = rates.stream()
                .filter(Rate::isSpreadNarrow)
                .collect(Collectors.toList());
    }

}
