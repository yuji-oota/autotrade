package autotrade.local;


import java.time.Duration;

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
        System.out.println(calcLot(12, 0, 18));
        System.out.println(calcLot(12, 10, 18));
    }

    private static int calcLot(int initialLot, int lot, int counter) {
        int target = initialLot < counter ? counter : initialLot;
        if (lot < target) {
            int diff = target - lot;
            if (diff <= 10) {
                return diff;
            } else {
                return 10;
            }
        }
        return 1;
    }
}
