package autotrade.local.material;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.stream.Stream;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class Rate implements Serializable {

    private Pair pair;
    private String rawAsk;
    private String rawBid;
    private int ask;
    private int bid;
    private LocalDateTime timestamp;

    public int getSpread() {
        return ask - bid;
    }

    public int getMiddle() {
        return (ask + bid) / 2;
    }

    public boolean isAskGt(Rate rate) {
        return isAskGt(rate.getAsk());
    }

    public boolean isBidLt(Rate rate) {
        return isBidLt(rate.getBid());
    }

    public boolean isAskLt(Rate rate) {
        return isAskLt(rate.getAsk());
    }

    public boolean isBidGt(Rate rate) {
        return isBidGt(rate.getBid());
    }

    public boolean isAskGt(int ask) {
        return this.ask > ask;
    }

    public boolean isBidLt(int bid) {
        return this.bid < bid;
    }

    public boolean isAskLt(int ask) {
        return this.ask < ask;
    }

    public boolean isBidGt(int bid) {
        return this.bid > bid;
    }

    public boolean isAskGe(int ask) {
        return this.ask >= ask;
    }

    public boolean isBidLe(int bid) {
        return this.bid <= bid;
    }

    public boolean isAskLe(int ask) {
        return this.ask <= ask;
    }

    public boolean isBidGe(int bid) {
        return this.bid >= bid;
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
