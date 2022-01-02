package autotrade.local.material;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import autotrade.local.utility.AutoTradeProperties;

public enum CurrencyPairBackup {
    USDJPY, EURUSD, GBPUSD, AUDUSD,
    ;
    
    private final static DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_TIME
            .withResolverStyle(ResolverStyle.LENIENT);

    private final static List<Map<String, Object>> listMap = AutoTradeProperties.getListMap("autotrade.pairs");
    private final static Map<String, Map<String, Object>> pairProperties = listMap.stream()
            .collect(Collectors.toMap(m -> m.get("name").toString(), Function.identity()));
    private final static List<String> descriptions = Stream.of(CurrencyPairBackup.values()).map(CurrencyPairBackup::getDescription)
            .toList();
    private final static List<String> names = Stream.of(CurrencyPairBackup.values()).map(CurrencyPairBackup::name).toList();
    private final static List<CurrencyPairBackup> pairs = List.of(CurrencyPairBackup.values());

    private Map<String, Object> getPairPropertie() {
        return pairProperties.get(this.name());
    }

    public int getMinSpread() {
        return (int) getPairPropertie().get("minSpread");
    }

    public int getMarginRequirement() {
        return (int) getPairPropertie().get("marginRequirement");
    }

    public boolean isHandleable(LocalTime time) {
        String rawStartTime = getPairPropertie().get("handleStart").toString();
        String rawEndTime = getPairPropertie().get("handleEnd").toString();
        LocalTime startTime = LocalTime.parse(rawStartTime, timeFormatter);
        LocalTime endTime = LocalTime.parse(rawEndTime, timeFormatter);

        if (startTime.equals(endTime)) {
            return true;
        }
        if (startTime.equals(time)) {
            return true;
        }
        if (startTime.isBefore(endTime)) {
            return startTime.isBefore(time) && time.isBefore(endTime);
        }
        if (LocalTime.MIN.equals(time)) {
            return true;
        }
        return (startTime.isBefore(time) && time.isBefore(LocalTime.MAX))
                || (LocalTime.MIN.isBefore(time) && time.isBefore(endTime));
    }

    public int getLimitLot(int margin) {
        return (int) (margin / getMarginRequirement() * 0.9);
    }

    public String getDescription() {
        return new StringBuilder(this.name()).insert(3, "/").toString();
    }

    public BigDecimal getProfitMagnification() {
        return new BigDecimal(getPairPropertie().get("profitMagnification").toString());
    }

    public static List<String> getDescriptions() {
        return descriptions;
    }

    public static List<String> getNames() {
        return names;
    }

    public static List<CurrencyPairBackup> getPairs() {
        return pairs;
    }
}
