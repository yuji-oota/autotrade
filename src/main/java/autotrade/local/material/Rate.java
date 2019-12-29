package autotrade.local.material;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Rate {
    private int ask;
    private int bid;
    private LocalDateTime timestamp;

    public int getSpread() {
        return ask - bid;
    }

    public boolean isDoubtful() {
        if (getSpread() > 70) {
            return true;
        }
        return false;
    }

    public Duration toCurrent() {
        return Duration.between(timestamp, LocalDateTime.now());
    }
}
