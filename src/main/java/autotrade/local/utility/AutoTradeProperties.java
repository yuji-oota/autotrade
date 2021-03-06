package autotrade.local.utility;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.Yaml;

import autotrade.local.exception.ApplicationException;
import lombok.extern.slf4j.Slf4j;

/**
 * ymlファイルの設定項目は以下を想定しています
fx:
  login:
    username: xxx
    password: xxx

autotrade:
  targetAmount:
    oneTrade: xxx
    oneDay: xxx
  inactive:
    start: xx:xx
    end: xx:xx
  lot:
    initial: xxx
    nextMagnification: xxx
    sameLimit:
      positive: xxx
      negative: xxx

aws:
  s3:
    bucketName: xxx
    accessKey: xxx
    secretKey: xxx
  elasticache:
    redis:
      uri: xxx
      channel: xxx
 *
 */
@Slf4j
public class AutoTradeProperties {

    private static Map<String, Object> properties;

    private AutoTradeProperties() {}

    static {
        try {
            properties = new Yaml().load(Files.newInputStream(Paths.get("application.yml")));
            log.info("properties loaded successfully.");
        } catch (IOException e) {
            throw new ApplicationException(e);
        }
    }

    public static String get(String key) {
        return resolvePropertie(key, properties).toString();
    }
    public static int getInt(String key) {
        return resolvePropertie(key, properties);
    }
    public static BigDecimal getBigDecimal(String key) {
        return new BigDecimal(get(key));
    }
    public static List<String> getList(String key) {
        return resolvePropertie(key, properties);
    }
    public static boolean getBoolean(String key) {
        return resolvePropertie(key, properties);
    }
    public static Map<String, Object> getMap(String key) {
        return resolvePropertie(key, properties);
    }
    public static List<Map<String, Object>> getListMap(String key) {
        return resolvePropertie(key, properties);
    }

    @SuppressWarnings("unchecked")
    private static <T> T resolvePropertie(String key, Map<String, Object> map) {
        List<String> keys = new LinkedList<>(Arrays.asList(key.split("\\.")));
        Object value = map.get(keys.get(0));
        if (keys.size() > 1) {
            keys.remove(0);
            return resolvePropertie(keys.stream().collect(Collectors.joining(".")), (Map<String, Object>) value);
        }
        return (T) value;
    }

}
