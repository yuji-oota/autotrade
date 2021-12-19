package autotrade.local;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

import org.junit.jupiter.api.Test;

import autotrade.local.utility.AutoTradeProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class Temporary {

    @AllArgsConstructor
    private enum OrderTerm {
        SHORT(Duration.ofSeconds(
                AutoTradeProperties.getInt("autoTraderEleventh.order.direction.duration.seconds.short"))), LONG(
                        Duration.ofSeconds(AutoTradeProperties
                                .getInt("autoTraderEleventh.order.direction.duration.seconds.long")));

        @Getter
        private Duration duration;

    }

    @Test
    public void test() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.LENIENT);
        System.out.println(LocalDateTime.parse("2001-11-12T23:10", formatter));
        System.out.println(LocalDateTime.parse("2001-11-12T25:10", formatter));
    }

    @Test
    public void test02() {
        System.out.println(BigDecimal.ONE
                .subtract(BigDecimal.valueOf(1000).divide(BigDecimal.valueOf(-10000),
                        new MathContext(2, RoundingMode.HALF_UP)))
                .multiply(BigDecimal.valueOf(100))
                .intValue());
    }

    @Test
    public void test03() {
        System.out.println("123.456".matches(".*99."));
        System.out.println("123.996".matches(".*99."));
        System.out.println("123.9961".matches(".*99."));
        System.out.println("123.006".matches(".*00."));
        System.out.println("123.0061".matches(".*00."));
    }

    @Test
    public void test04() {
        System.out.println(new BigDecimal(300000).multiply(new BigDecimal("0.005")).intValue());
    }

}
