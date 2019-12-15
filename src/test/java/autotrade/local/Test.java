package autotrade.local;

import java.time.Duration;
import java.time.LocalDateTime;

public class Test {

    public static void main(String[] args) {

        LocalDateTime ldt1 = LocalDateTime.now().minusDays(2);
        System.out.println(Duration.between(ldt1, LocalDateTime.now()).toDays());
        System.out.println(Duration.between(ldt1, LocalDateTime.now()).toHours());
        System.out.println(Duration.between(ldt1, LocalDateTime.now()).toMinutes());
        System.out.println(Duration.between(ldt1, LocalDateTime.now()).toSeconds());
        System.out.println(Duration.between(ldt1, LocalDateTime.now()).toMillis());
    }

}
