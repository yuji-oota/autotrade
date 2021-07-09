package autotrade.local;


import java.time.Duration;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.Test;

import autotrade.local.utility.AutoTradeProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class Temporary {

    @AllArgsConstructor
    private enum OrderTerm {
         SHORT(Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderEleventh.order.direction.duration.seconds.short")))
        ,LONG(Duration.ofSeconds(AutoTradeProperties.getInt("autoTraderEleventh.order.direction.duration.seconds.long")))
        ;

        @Getter
        private Duration duration;

        public OrderTerm change() {
            return this == SHORT ? LONG : SHORT;
        }

    }

    @Test
    public void test() {
        System.out.println(OrderTerm.SHORT.change());
        System.out.println(OrderTerm.LONG.change());
    }

    @Test
    public void test02() {
        int target = 30;
        System.out.println(calcLot(0, () -> 0));
        System.out.println(calcLot(target, () -> 10));
        System.out.println(calcLot(target, () -> 15));
        System.out.println(calcLot(target, () -> 19));
        System.out.println(calcLot(target, () -> 20));
        System.out.println(calcLot(target, () -> 25));
        System.out.println(calcLot(target, () -> 29));
        System.out.println(calcLot(target, () -> 30));
        System.out.println(calcLot(target, () -> 31));
        System.out.println(calcLot(target, () -> 32));
    }

    private static int calcLot(int initialLot, IntSupplier lot) {
        if (lot.getAsInt() < initialLot) {
            int diff = initialLot - lot.getAsInt();
            if (diff <= 10) {
                return diff;
            } else {
                return 10;
            }
        }
        return 1;
    }
}
