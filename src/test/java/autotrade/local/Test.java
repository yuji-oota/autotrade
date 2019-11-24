package autotrade.local;

import java.util.stream.Stream;

import autotrade.local.material.PositionStatus;

public class Test {

    public static void main(String[] args) {

        PositionStatus positionStatus = PositionStatus.valueOf("NONE");
        System.out.println(positionStatus);
        Stream.of(PositionStatus.values()).forEach(System.out::println);
        System.out.println( Stream.of(PositionStatus.values()).anyMatch(v -> v.name().equals("xxx")));
        System.out.println( Stream.of(PositionStatus.values()).anyMatch(v -> v.name().equals("NONE")));
    }

}
