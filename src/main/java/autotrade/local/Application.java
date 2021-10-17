package autotrade.local;

import autotrade.local.autotrader.AutoTrader;
import autotrade.local.autotrader.impl.AutoTrader19th;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    public static void main(String[] args) {

        AutoTrader autoTrader = new AutoTrader19th();
        try {
            while(true) {
                autoTrader.operation();
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }

    }

}
