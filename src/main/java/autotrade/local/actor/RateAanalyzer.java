package autotrade.local.actor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import autotrade.local.material.Rate;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class RateAanalyzer {

    private Rate currentRate;
    private List<Rate> rates;
    private int askThreshold;
    private int bidThreshold;

    public RateAanalyzer() {
        rates = new ArrayList<>();
        askThreshold = Integer.MAX_VALUE;
        bidThreshold = Integer.MIN_VALUE;
    }

    public void add(Rate rate) {
        if (rate.getSpread() > 60) {
            log.info("doubtful rate is added {}", rate);
        }
        currentRate = rate;
        rates.add(currentRate);
        rates = rates.stream()
                .filter(r -> ChronoUnit.MINUTES.between(r.getTimestamp(), LocalDateTime.now()) <= 15)
                .collect(Collectors.toList());

        // 売買閾値設定
        askThreshold = maxWithin(10);
        bidThreshold = minWithin(10);
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
        askThreshold = maxWithin(minutes);
        bidThreshold = minWithin(minutes);
    }

    public int rangeWithin(int minutes) {
        return maxWithin(minutes) - minWithin(minutes);
    }

    private int maxWithin(int minutes) {
        return rates.stream()
                .filter(r -> ChronoUnit.MINUTES.between(r.getTimestamp(), LocalDateTime.now()) <= minutes)
                .map(Rate::getAsk)
                .max(Comparator.naturalOrder())
                .orElse(0);
    }
    private int minWithin(int minutes) {
        return rates.stream()
                .filter(r -> ChronoUnit.MINUTES.between(r.getTimestamp(), LocalDateTime.now()) <= minutes)
                .map(Rate::getBid)
                .min(Comparator.naturalOrder())
                .orElse(0);
    }
}
