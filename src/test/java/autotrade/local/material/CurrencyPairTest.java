package autotrade.local.material;

import org.junit.jupiter.api.Test;

class CurrencyPairTest {

    @Test
    void test() {
        System.out.println(CurrencyPair.AUDUSD.getMinSpread());
        System.out.println(CurrencyPair.AUDUSD.getMarginRequirement());
        System.out.println(CurrencyPair.AUDUSD.getHandleMarket());
    }

}
