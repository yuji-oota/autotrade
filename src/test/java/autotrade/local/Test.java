package autotrade.local;

import java.time.Duration;
import java.time.LocalDateTime;

public class Test {

    public static void main(String[] args) {
        System.out.println(Duration.between(LocalDateTime.now(), LocalDateTime.now().plusMinutes(5)).toMillis());
        System.out.println(Duration.between(LocalDateTime.now(), LocalDateTime.now()).toMillis());
        System.out.println(Duration.between(LocalDateTime.now(), LocalDateTime.now().minusMinutes(5)).toMillis());
        System.out.println(Duration.between(LocalDateTime.now(), LocalDateTime.now().plusMinutes(5)).isNegative());
        System.out.println(Duration.between(LocalDateTime.now(), LocalDateTime.now()).isNegative());
        System.out.println(Duration.between(LocalDateTime.now(), LocalDateTime.now().minusMinutes(5)).isNegative());
    }

}
