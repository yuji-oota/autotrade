package autotrade.local.material;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Rate implements Serializable {
    private int ask;
    private int bid;
    private LocalDateTime timestamp;

    public int getSpread() {
        return ask - bid;
    }

    public boolean isDoubtful() {
        if (getSpread() > 100) {
            return true;
        }
        return false;
    }

    public Duration passed() {
        return Duration.between(timestamp, LocalDateTime.now());
    }

    public int getMiddle() {
        return (ask + bid) / 2;
    }
}
