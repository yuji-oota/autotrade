package autotrade.local;

import autotrade.local.autotrader.AutoTrader;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    public static void main(String[] args) {

        AutoTrader autoTrader = AutoTradeUtils.newInstance(AutoTradeProperties.get("autotrade.implementation"));
        try {
            while(true) {
                autoTrader.operation();
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }

    }

}
