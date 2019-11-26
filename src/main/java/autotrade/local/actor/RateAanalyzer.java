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
        if (rate.getAsk() - rate.getBid() > 60) {
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
        if (50 < range && range <= 100) {
            askThreshold = maxWithin(2);
            bidThreshold = minWithin(2);
        }
        if (100 < range) {
            askThreshold = maxWithin(1);
            bidThreshold = minWithin(1);
        }
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
