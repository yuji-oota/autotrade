package autotrade.local.material;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum CurrencyPair {
    USDJPY(2),
    EURUSD(4),
    EURJPY(5),
    GBPUSD(8),
    GBPJPY(10),
    AUDUSD(8),
    AUDJPY(7),
    ;

    private int minSpread;

    public String getDescription() {
        return new StringBuilder(this.name()).insert(3, "/").toString();
    }

    public static List<String> getDescriptions() {
        return Stream.of(CurrencyPair.values()).map(CurrencyPair::getDescription).collect(Collectors.toList());
    }
}
