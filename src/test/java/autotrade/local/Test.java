package autotrade.local;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Test {

    public static void main(String[] args) {
        System.out.println(DateTimeFormatter.ISO_LOCAL_TIME.parse("10:00"));
        System.out.println(LocalTime.from(DateTimeFormatter.ISO_LOCAL_TIME.parse("10:00")));


    }

}
