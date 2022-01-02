package autotrade.local;

import autotrade.local.autotrader.AbstractAutoTrader;
import autotrade.local.utility.AutoTradeProperties;
import autotrade.local.utility.AutoTradeUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Application {

    public static void main(String[] args) {

        AbstractAutoTrader autoTrader = AutoTradeUtils.newInstance(AutoTradeProperties.get("autotrade.implementation"));
        try {
            autoTrader.preOperation();
            while(true) {
                autoTrader.operation();
            }
        } catch(Exception e) {
            log.error(e.getMessage(), e);
        }

    }

}
