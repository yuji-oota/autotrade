package autotrade.local;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

import org.junit.jupiter.api.Test;

public class Temporary {

    @Test
    public void test() {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.LENIENT);
        System.out.println(LocalDateTime.parse("2001-11-12T23:10", formatter));
        System.out.println(LocalDateTime.parse("2001-11-12T25:10", formatter));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_TIME.withResolverStyle(ResolverStyle.LENIENT);
        System.out.println(LocalTime.parse("25:10", timeFormatter));
    }

    @Test
    public void test02() {
        System.out.println(BigDecimal.ONE
                .subtract(BigDecimal.valueOf(300).divide(BigDecimal.valueOf(-1200),
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

    @Test
    public void test05() {
        int i = 123001 / 1000 * 1000;
        System.out.println(i);
        System.out.println(123456 % 1000);
        System.out.println(123001 / 1000 * 1000);
        System.out.println(123000 / 1000 * 1000);
        System.out.println(123999 / 1000 * 1000);
        System.out.println(123998 / 1000 * 1000);
        System.out.println(122001 / 1000 * 1000);
        System.out.println(122000 / 1000 * 1000);
        System.out.println(122999 / 1000 * 1000);
        System.out.println(122998 / 1000 * 1000);
    }

}
