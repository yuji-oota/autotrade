package autotrade.local.utility;

import autotrade.local.exception.ApplicationException;

public class AutoTradeUtils {

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
}
