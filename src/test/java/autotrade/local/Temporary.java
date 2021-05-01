package autotrade.local;


import java.math.BigDecimal;
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
        int target = 30;
        System.out.println(calcLot(target, 10));
        System.out.println(calcLot(target, 15));
        System.out.println(calcLot(target, 19));
        System.out.println(calcLot(target, 20));
        System.out.println(calcLot(target, 25));
        System.out.println(calcLot(target, 29));
        System.out.println(calcLot(target, 30));
        System.out.println(calcLot(target, 35));
        System.out.println(calcLot(target, 39));
        System.out.println(calcLot(target, 40));
        System.out.println(calcLot(target, 45));
        System.out.println(calcLot(target, 49));
    }

    private int calcLot(int targetLot, int otherLot) {
        int lot = 1;
        BigDecimal other = BigDecimal.valueOf(otherLot * 1.5);
        if (targetLot < other.intValue()) {
            lot = 2;
            lot = lot + (( other.intValue() - targetLot) / 10);
        }
        return lot;
    }

}
