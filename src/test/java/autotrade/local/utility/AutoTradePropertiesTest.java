package autotrade.local.utility;

import org.junit.Test;

public class AutoTradePropertiesTest {

    @Test
    public void test() {
        System.out.println(AutoTradeProperties.get("autotrade.lot.initial.base.pair"));
        System.out.println(AutoTradeProperties.getInt("autotrade.targetAmount.oneDay"));
        System.out.println(AutoTradeProperties.getList("autotrade.order.pairs"));
        System.out.println(AutoTradeProperties.getBigDecimal("autotrade.lot.countertrading.magnification"));
    }

}
