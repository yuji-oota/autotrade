package autotrade.local;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
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

    @Test
    public void test06() {
        int maxRatio = 75;
        int minRatio = 25;
        int limit = 150;
        int initial = 20;
        int current = 70;
        BigDecimal diffRatio = new BigDecimal(maxRatio - minRatio);
        BigDecimal limitSubInitial = new BigDecimal(
                limit - initial);
        BigDecimal currentSubInitial = new BigDecimal(current - initial);
        BigDecimal progressUnit = limitSubInitial.divide(diffRatio, 1, RoundingMode.HALF_UP);
        int progress = maxRatio - currentSubInitial.divide(progressUnit, 0, RoundingMode.HALF_UP).intValue();
        System.out.println(progress);
    }

    @Test
    public void test07() {
        Duration duration = Duration.ofSeconds(600);
        System.out.println(duration.minusMillis(1 * 5700));
        System.out.println(duration.minusMillis(10 * 5700));
        System.out.println(duration.minusMillis(20 * 5700));
        System.out.println(duration.minusMillis(30 * 5700));
        System.out.println(duration.minusMillis(40 * 5700));
        System.out.println(duration.minusMillis(50 * 5700));
        System.out.println(duration.minusMillis(60 * 5700));
        System.out.println(duration.minusMillis(70 * 5700));
        System.out.println(duration.minusMillis(80 * 5700));
        System.out.println(duration.minusMillis(90 * 5700));
        System.out.println(duration.minusMillis(99 * 5700));
    }

    @Test
    public void test08() {
        BigDecimal currentRange = new BigDecimal(20);
        BigDecimal halfRange = new BigDecimal(20);

        currentRange = new BigDecimal(20);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(19);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(18);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(17);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(16);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(15);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(14);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(10);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(8);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(6);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(4);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(2);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(1);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.UP).intValue());
        currentRange = new BigDecimal(0);
        System.out.println(new BigDecimal(5)
                .multiply(currentRange)
                .divide(halfRange, 0, RoundingMode.HALF_UP).intValue());


    }

    @Test
    public void test09() {
        System.out.println(1/2);
        System.out.println(2/2);
        System.out.println(3/2);
        System.out.println(4/2);
        System.out.println(5/2);
    }
}
