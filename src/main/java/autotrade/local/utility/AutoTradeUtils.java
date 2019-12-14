package autotrade.local.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import autotrade.local.exception.ApplicationException;

public class AutoTradeUtils {

    private static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new ApplicationException(e);
        }
    }

    public static int toInt(String numStr) {
        numStr = numStr.replace(",", "").replace(".", "");
        if (numStr.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(numStr);
    }

    public static String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new ApplicationException(e);
        }
    }
}
