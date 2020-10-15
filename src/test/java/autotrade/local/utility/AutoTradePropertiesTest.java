package autotrade.local.utility;

import org.junit.jupiter.api.Test;

public class AutoTradePropertiesTest {

    @Test
    public void test() {
        System.out.println(AutoTradeProperties.get("autotrade.lot.initial.base.pair"));
        System.out.println(AutoTradeProperties.getList("autotrade.order.pairs"));
        System.out.println(AutoTradeProperties.getBigDecimal("autotrade.lot.countertrading.magnification"));
        System.out.println(AutoTradeProperties.getMap("autotrade.lot.marginRequirement"));
        System.out.println(AutoTradeProperties.getMap("autotrade.lot.marginRequirement").get("USDJPY").toString());
    }

}
