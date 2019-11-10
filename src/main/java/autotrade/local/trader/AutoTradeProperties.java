package autotrade.local.trader;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.Yaml;

public class AutoTradeProperties {

    private static AutoTradeProperties instance;
    private static Map<String, Object> properties;

    private AutoTradeProperties() {
        properties = new Yaml().load(this.getClass().getClassLoader().getResourceAsStream("application.yml"));
        System.out.println(properties);
    }

    static {
        if (Objects.isNull(instance)) {
            instance = new AutoTradeProperties();
        }
    }

    public static String get(String key) {
        return resolvePropertie(new LinkedList<>(Arrays.asList(key.split("\\."))), properties);
    }

    @SuppressWarnings("unchecked")
    private static String resolvePropertie(List<String> keys, Map<String, Object> map) {
        Object value = map.get(keys.get(0));
        if (value instanceof Map) {
            keys.remove(0);
            return resolvePropertie(keys, (Map<String, Object>) value);
        }
        return value.toString();
    }
}
