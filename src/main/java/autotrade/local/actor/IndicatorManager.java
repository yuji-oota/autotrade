package autotrade.local.actor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import autotrade.local.material.Indicator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IndicatorManager {

    private LocalDateTime nextIndicator;
    private LocalDateTime prevIndicator;
    private List<Indicator> indicators;

    @Getter
    private List<LocalDateTime> indicatorDateTimes;

    public IndicatorManager() {
        this.indicatorDateTimes = new ArrayList<>();
        this.nextIndicator = LocalDateTime.now();
        this.prevIndicator = LocalDateTime.now();
    }

    private LocalDateTime getNextIndicate() {
        if (LocalDateTime.now().isAfter(nextIndicator)) {
            prevIndicator = nextIndicator;
            nextIndicator = indicatorDateTimes.stream()
                    .filter(LocalDateTime.now()::isBefore)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now().plusDays(2));
            log.info("next indicator will come at {}", nextIndicator);
        }
        return nextIndicator;
    }

    public boolean isNextIndicatorWithin(Duration duration) {
        if (Duration.between(LocalDateTime.now(), getNextIndicate()).toMillis() < duration.toMillis()) {
            return true;
        }
        return false;
    }

    public boolean isPrevIndicatorWithin(Duration duration) {
        if (Duration.between(prevIndicator, LocalDateTime.now()).toMillis() < duration.toMillis()) {
            return true;
        }
        return false;
    }

    public boolean isIndicatorAround(Duration duration) {
        return isNextIndicatorWithin(duration) || isPrevIndicatorWithin(duration);
    }

    public boolean hasIndicator() {
        return !indicatorDateTimes.isEmpty();
    }

    public void addIndicators(List<Indicator> indicators) {
        this.indicators = indicators;
        this.indicatorDateTimes.addAll(
                indicators.stream()
                        .map(Indicator::getDatetime)
                        .distinct()
                        .sorted()
                        .toList());
    }

    public void printIndicators() {
        log.info("indicator datetime list:{}", indicatorDateTimes);
    }
}
