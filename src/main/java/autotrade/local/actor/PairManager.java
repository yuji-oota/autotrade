package autotrade.local.actor;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import autotrade.local.utility.AutoTradeProperties;

public class PairManager {

    private Map<String, Map<String, Object>> pairProperties;
    private Map<String, Object> pairPropertie;

    public PairManager(String pair) {
        List<Map<String, Object>> listMap = AutoTradeProperties.getListMap("autotrade.pairs");
        pairProperties = listMap.stream().collect(Collectors.toMap(m -> m.get("name").toString(), Function.identity()));
        changePair(pair);
    }

    public void changePair(String pair) {
        pairPropertie = pairProperties.get(pair);
    }

    public String getName() {
        return pairPropertie.get("name").toString();
    }

    public String getDescription() {
        return new StringBuilder(getName()).insert(3, "/").toString();
    }

    public int getMinSpread() {
        return Integer.parseInt(pairPropertie.get("minSpread").toString());
    }

    public int getMarginRequirement() {
        return Integer.parseInt(pairPropertie.get("marginRequirement").toString());
    }

    public int getLimit(int margin) {
        return (int) (margin / getMarginRequirement() * 0.9);
    }

}
