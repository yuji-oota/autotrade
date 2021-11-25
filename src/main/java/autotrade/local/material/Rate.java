package autotrade.local.material;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

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

    public boolean isNearThousand() {
        return isNearThousand(rawAsk)
                || isNearThousand(rawBid);
    }

    private static boolean isNearThousand(String decimal) {
        if ("0".equals(getHundredPart(decimal))) {
            return true;
        }
        ;
        if ("9".equals(getHundredPart(decimal))) {
            return true;
        }
        ;
        return false;
    }

    private static String getHundredPart(String decimal) {
        String noPeriod = decimal.replace(".", "");
        return noPeriod.substring(noPeriod.length() - 3).substring(0, 1);
    }
}
