package autotrade.local;


import java.io.IOException;
import java.time.LocalDateTime;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;

public class Test {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Snapshot snapshot = Snapshot.builder()
                .askAverageRate(100)
                .bidAverageRate(90)
                .askLot(160)
                .bidLot(160)
                .askPipProfit(5)
                .bidPipProfit(-50)
                .rate(Rate.builder().ask(110).bid(118).timestamp(LocalDateTime.now()).build())
                .build();
        System.out.println(snapshot);
    }


}
