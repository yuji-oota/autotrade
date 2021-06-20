package autotrade.local.material;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CurrencyPair {
    USDJPY(2),
    EURUSD(3),
    EURJPY(5),
    GBPUSD(8),
    GBPJPY(10),
    AUDUSD(8),
    AUDJPY(7),
    ;

    private final static List<String> descriptions = Collections.unmodifiableList(
            Stream.of(CurrencyPair.values()).map(CurrencyPair::getDescription).collect(Collectors.toList()));
    private final static List<String> names = Collections.unmodifiableList(
            Stream.of(CurrencyPair.values()).map(CurrencyPair::name).collect(Collectors.toList()));
    private int minSpread;

    public String getDescription() {
        return new StringBuilder(this.name()).insert(3, "/").toString();
    }
    public boolean isSpreadWiden(int spread) {
        return minSpread < spread;
    }

    public static List<String> getDescriptions() {
        return descriptions;
    }
    public static List<String> getNames() {
        return names;
    }
}
