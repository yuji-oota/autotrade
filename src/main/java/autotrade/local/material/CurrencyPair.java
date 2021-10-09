package autotrade.local.material;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import autotrade.local.utility.AutoTradeProperties;

public enum CurrencyPair {
    USDJPY,
    EURUSD,
    EURJPY,
    GBPUSD,
    GBPJPY,
    AUDUSD,
    AUDJPY,
    ;

    private final static List<Map<String, Object>> listMap = AutoTradeProperties.getListMap("autotrade.pairs");
    private final static Map<String, Map<String, Object>> pairProperties = listMap.stream().collect(Collectors.toMap(m -> m.get("name").toString(), Function.identity()));
    private final static List<String> descriptions = Collections.unmodifiableList(
            Stream.of(CurrencyPair.values()).map(CurrencyPair::getDescription).collect(Collectors.toList()));
    private final static List<String> names = Collections.unmodifiableList(
            Stream.of(CurrencyPair.values()).map(CurrencyPair::name).collect(Collectors.toList()));

    private Map<String, Object> getPairPropertie() {
        return pairProperties.get(this.name());
    }
    public int getMinSpread() {
        return (int) getPairPropertie().get("minSpread");
    }
    public int getMarginRequirement() {
        return (int) getPairPropertie().get("marginRequirement");
    }
    public int getLimitLot(int margin) {
        return (int) (margin / getMarginRequirement() * 0.9);
    }
    public String getDescription() {
        return new StringBuilder(this.name()).insert(3, "/").toString();
    }
    public static List<String> getDescriptions() {
        return descriptions;
    }
    public static List<String> getNames() {
        return names;
    }
}
