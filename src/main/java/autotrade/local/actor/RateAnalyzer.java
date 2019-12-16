package autotrade.local.actor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public RateAnalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
    }

    public void add(Rate rate) {
        if (rate.isDoubtful()) {
            log.info("doubtful rate is added {}", rate);
        } else {
            rates.add(rate);
        }
        rates = rates.stream()
                .filter(r -> r.toCurrent().toMillis() <= Duration.ofMinutes(15).toMillis())
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
                .filter(r -> r.toCurrent().toMillis() <= duration.toMillis())
                .map(Rate::getAsk)
                .max(Comparator.naturalOrder())
                .orElse(Integer.MAX_VALUE);
    }
    public int minWithin(Duration duration) {
        return rates.stream()
                .filter(r -> r.toCurrent().toMillis() <= duration.toMillis())
                .map(Rate::getBid)
                .min(Comparator.naturalOrder())
                .orElse(Integer.MIN_VALUE);
    }

    public boolean isMoving() {
        if (rangeWithin(Duration.ofSeconds(1)) == 2) {
            return false;
        }
        return true;
    }
}
