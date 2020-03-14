package autotrade.local.utility;

import org.junit.Test;

public class AutoTradePropertiesTest {

    @Test
    public void test() {
        System.out.println(AutoTradeProperties.get("autotrade.targetAmount.oneDay"));
    }

}
