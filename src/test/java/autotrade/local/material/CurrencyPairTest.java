package autotrade.local.material;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class CurrencyPairTest {

    @Test
    void test() {
        System.out.println(CurrencyPair.AUDUSD.getMinSpread());
        System.out.println(CurrencyPair.AUDUSD.getMarginRequirement());
        System.out.println(CurrencyPair.EURUSD.getProfitMagnification());
        System.out.println(CurrencyPair.GBPUSD.getProfitMagnification());
        System.out.println(CurrencyPair.EURUSD.isHandleable(LocalTime.of(7, 0)));
        System.out.println(CurrencyPair.EURUSD.isHandleable(LocalTime.of(8, 0)));
        System.out.println(CurrencyPair.EURUSD.isHandleable(LocalTime.of(8, 1)));
        System.out.println(CurrencyPair.EURUSD.isHandleable(LocalTime.of(23, 0)));
        System.out.println(CurrencyPair.EURUSD.isHandleable(LocalTime.of(0, 0)));
        System.out.println(CurrencyPair.EURUSD.isHandleable(LocalTime.of(1, 0)));
        System.out.println(CurrencyPair.EURUSD.isHandleable(LocalTime.of(2, 0)));
        System.out.println(CurrencyPair.USDJPY.isHandleable(LocalTime.of(7, 0)));
        System.out.println(CurrencyPair.USDJPY.isHandleable(LocalTime.of(8, 0)));
        System.out.println(CurrencyPair.USDJPY.isHandleable(LocalTime.of(8, 1)));
        System.out.println(CurrencyPair.USDJPY.isHandleable(LocalTime.of(14, 59)));
        System.out.println(CurrencyPair.USDJPY.isHandleable(LocalTime.of(15, 0)));
    }

}
