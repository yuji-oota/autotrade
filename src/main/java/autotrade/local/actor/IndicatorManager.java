package autotrade.local.actor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IndicatorManager {

    private LocalDateTime nextIndicator;
    private LocalDateTime prevIndicator;
    private List<LocalDateTime> indicators;

    public IndicatorManager(List<LocalDateTime> indicators) {
        this.indicators = indicators;
        this.nextIndicator = LocalDateTime.now();
        this.prevIndicator = LocalDateTime.now();
    }

    private LocalDateTime getNextIndicate() {
        if (LocalDateTime.now().isAfter(nextIndicator)) {
            prevIndicator = nextIndicator;
            nextIndicator = indicators.stream()
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
}
