package autotrade.local;

import autotrade.local.actor.AutoTrader;
import autotrade.local.actor.AutoTraderEighth;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    public static void main(String[] args) {

        AutoTrader autoTrader = new AutoTraderEighth();
        try {
            while(true) {
                autoTrader.operation();
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }

    }

}
