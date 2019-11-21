package autotrade.local.actor;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IndicatorManager {

    private LocalDateTime nextIndicator;
    private LocalDateTime prevIndicator;
    private List<LocalDateTime> indicators;

    public IndicatorManager(List<LocalDateTime> indicators) {
        this.indicators = indicators;
        this.nextIndicator = LocalDateTime.MIN;
        this.prevIndicator = LocalDateTime.MIN;
    }

    private LocalDateTime getNextIndicate() {
        if (LocalDateTime.now().isAfter(nextIndicator)) {
            prevIndicator = nextIndicator;
            nextIndicator = indicators.stream()
                    .filter(LocalDateTime.now()::isBefore)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.MAX);
            log.info("next indicator will come at {}", nextIndicator);
        }
        return nextIndicator;
    }

    public boolean isNextIndicatorWithin(long minute) {
        if (ChronoUnit.MINUTES.between(LocalDateTime.now(), getNextIndicate()) < minute) {
            return true;
        }
        return false;
    }
    public boolean isPrevIndicatorWithin(long minute) {
        if (ChronoUnit.MINUTES.between(prevIndicator, LocalDateTime.now()) < minute) {
            return true;
        }
        return false;
    }
}
