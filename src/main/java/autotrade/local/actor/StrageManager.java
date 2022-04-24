package autotrade.local.actor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import autotrade.local.utility.AutoTradeUtils;

public class StrageManager {

    private static Map<String, Object> map = new HashMap<>();
    private static final Path SAVE_FILE_PATH = Paths.get("localSave", "strageManagerMap");
    
    public static void put(String key, Object value) {
        map.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return (T) map.get(key);
    }
    
    public static void saveLocal() {
        AutoTradeUtils.localSave(SAVE_FILE_PATH, map);
    }
    
    public static void loadLocal() {
        map = AutoTradeUtils.localLoad(SAVE_FILE_PATH);
    }
}
