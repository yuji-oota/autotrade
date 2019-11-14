package autotrade.local.trader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yaml.snakeyaml.Yaml;

import autotrade.local.exception.AppliationException;
import lombok.extern.slf4j.Slf4j;

/**
 * ymlファイルの設定項目は以下を想定しています

login:
  username: xxx
  password: xxx

autotrade:
  targetAmount: xxx
  initialLot: xxx
  sameLimit: xxx
  inactive:
    start: xx:xx
    end: xx:xx

 *
 */
@Slf4j
public class AutoTradeProperties {

    private static AutoTradeProperties instance;
    private static Map<String, Object> properties;

    private AutoTradeProperties() {
        try {
            properties = new Yaml().load(Files.newInputStream(Paths.get("application.yml")));
            log.info("properties:{}", properties);
        } catch (IOException e) {
            throw new AppliationException(e);
        }
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
