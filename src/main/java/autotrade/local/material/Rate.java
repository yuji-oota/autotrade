package autotrade.local.material;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Stream;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Rate implements Serializable {

    private CurrencyPair pair;
    private String rawAsk;
    private String rawBid;
    private int ask;
    private int bid;
    private LocalDateTime timestamp;

    public int getSpread() {
        return ask - bid;
    }

    public Duration passed() {
        return Duration.between(timestamp, LocalDateTime.now());
    }

    public int getMiddle() {
        return (ask + bid) / 2;
    }

    public boolean isAbobe(Rate rate) {
        return this.bid > rate.getBid();
    }

    public boolean isSpreadWiden() {
        if (Objects.isNull(pair)) {
            return true;
        }
        return pair.getMinSpread() < getSpread();
    }

    public boolean isSpreadNarrow() {
        return !isSpreadWiden();
    }

    public boolean anyMatch(String... regexs) {
        return Stream.of(regexs).anyMatch(regex -> {
            return rawAsk.matches(regex) || rawBid.matches(regex);
        });
    }
}
