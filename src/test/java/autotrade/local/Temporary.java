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
        System.out.println(calcLot(0, 9));
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
        System.out.println(calcLot(target, 50));
        System.out.println(calcLot(target, 60));
        System.out.println(calcLot(target, 70));
        System.out.println(calcLot(target, 80));
        System.out.println(calcLot(target, 90));
    }

    private int calcLot(int targetLot, int otherLot) {
        if (targetLot < otherLot) {
            int lotTobe = BigDecimal.valueOf(otherLot * 1.25).intValue();
            if (lotTobe > 80) {
                return lotTobe - 80;
            }
            return lotTobe - targetLot;
        }
        return 1;
    }
}
