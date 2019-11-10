package autotrade.local;

import autotrade.local.trader.AutoTrader;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    public static void main(String[] args) {

        try {
            while(true) {
                AutoTrader.getInstance().operation();
            }
        } catch(Exception e) {
            log.error(e.getMessage());
        }

    }

}
