package autotrade.local.trader;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class IndicateAnalyzer {

    private LocalDateTime nextIndicate;
    private List<LocalDateTime> indicates;

    public IndicateAnalyzer(List<LocalDateTime> indicates) {
        this.indicates = indicates;
        this.nextIndicate = LocalDateTime.MIN;
    }

    private LocalDateTime getNextIndicate() {
        if (LocalDateTime.now().isAfter(nextIndicate)) {
            nextIndicate = indicates.stream()
                    .filter(LocalDateTime.now()::isBefore)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.MAX);
        }
        return nextIndicate;
    }

    public boolean isNextIndicateWithin(long minute) {
        if (ChronoUnit.MINUTES.between(LocalDateTime.now(), getNextIndicate()) < minute) {
            return true;
        }
        return false;
    }
}
