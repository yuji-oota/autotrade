package autotrade.local.actor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import autotrade.local.material.Indicator;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IndicatorManager {

    private LocalDateTime nextDateTime;
    private LocalDateTime prevDateTime;
    private List<Indicator> indicators;
    private List<LocalDateTime> indicatorDateTimes;

    public IndicatorManager() {
        this.indicatorDateTimes = new ArrayList<>();
        this.nextDateTime = LocalDateTime.now();
        this.prevDateTime = LocalDateTime.now();
    }

    private LocalDateTime getNextDateTime() {
        if (LocalDateTime.now().isAfter(nextDateTime)) {
            prevDateTime = nextDateTime;
            nextDateTime = indicatorDateTimes.stream()
                    .filter(LocalDateTime.now()::isBefore)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now().plusDays(2));
            log.info("next indicator will come at {}", nextDateTime);
        }
        return nextDateTime;
    }

    public boolean isNextIndicatorWithin(Duration duration) {
        return LocalDateTime.now().plus(duration).isAfter(getNextDateTime());
    }

    public boolean isPrevIndicatorWithin(Duration duration) {
        return prevDateTime.plus(duration).isAfter(LocalDateTime.now());
    }

    public boolean isIndicatorAround(Duration before, Duration after) {
        return isNextIndicatorWithin(before) || isPrevIndicatorWithin(after);
    }

    public boolean isIndicatorBefore(Duration duration) {
        return LocalDateTime.now()
                .plus(duration)
                .truncatedTo(ChronoUnit.SECONDS)
                .isEqual(getNextDateTime());
    }

    public boolean hasIndicator() {
        return !indicatorDateTimes.isEmpty();
    }

    public void addIndicators(List<Indicator> indicators) {
        this.indicators = indicators;
        this.indicatorDateTimes.addAll(
                this.indicators.stream()
                        .map(Indicator::getDateTime)
                        .distinct()
                        .sorted()
                        .toList());
    }

    public void printIndicatorDateTimes() {
        log.info("indicator datetime list:{}", indicatorDateTimes);
    }

    public void printIndicators(LocalDate date) {
        log.info("{} indicators are as follows", date);
        indicators.stream()
                .filter(ind -> ind.getDateTime().toLocalDate().isEqual(date))
                .forEach(Indicator::print);
    }

    public void printNextIndicator() {
        log.info("next indicators are as follows");
        indicators.stream()
                .filter(ind -> ind.getDateTime().isEqual(getNextDateTime()))
                .forEach(Indicator::print);
    }

    public boolean isNextImportant() {
        return indicators.stream()
                .filter(ind -> ind.getDateTime().isEqual(getNextDateTime()))
                .filter(Indicator::isImportant)
                .count() > 0;
    }
}
