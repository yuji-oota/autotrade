package autotrade.local;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayDeque;

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
        System.out.println( LocalDateTime.parse("2001-11-12T23:10", formatter));
        System.out.println( LocalDateTime.parse("2001-11-12T25:10", formatter));
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

    @Test
    public void test03() {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        addQueue(queue, 1);
        addQueue(queue, 2);
        addQueue(queue, 3);
        addQueue(queue, 4);
        addQueue(queue, 5);
        queue.stream().forEach(System.out::println);
        addQueue(queue, 6);
        queue.stream().forEach(System.out::println);
        addQueue(queue, 7);
        queue.stream().forEach(System.out::println);
    }

    private void addQueue(ArrayDeque<Integer> queue, int i) {
        queue.add(i);
        if (queue.size() > 5) {
            queue.poll();
        }
        System.out.println(queue);
        System.out.println(queue.size());
        System.out.println(queue.getFirst());
        System.out.println(queue.getLast());
    }
}
