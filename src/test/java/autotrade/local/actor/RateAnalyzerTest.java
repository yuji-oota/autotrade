package autotrade.local.actor;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import autotrade.local.material.Rate;

public class RateAnalyzerTest {

    @Test
    public void test() {
        RateAnalyzer rateAnalyzer = new RateAnalyzer();
        rateAnalyzer.add(Rate.builder().ask(101).bid(100).timestamp(LocalDateTime.now().minusMinutes(9)).build());
        rateAnalyzer.add(Rate.builder().ask(101).bid(100).timestamp(LocalDateTime.now().minusMinutes(8)).build());
        rateAnalyzer.add(Rate.builder().ask(101).bid(100).timestamp(LocalDateTime.now().minusMinutes(7)).build());
        rateAnalyzer.add(Rate.builder().ask(101).bid(100).timestamp(LocalDateTime.now().minusMinutes(6)).build());
        rateAnalyzer.add(Rate.builder().ask(101).bid(100).timestamp(LocalDateTime.now().minusMinutes(5)).build());
        rateAnalyzer.add(Rate.builder().ask(201).bid(200).timestamp(LocalDateTime.now().minusMinutes(4)).build());
        rateAnalyzer.add(Rate.builder().ask(101).bid(100).timestamp(LocalDateTime.now().minusMinutes(3)).build());
        rateAnalyzer.add(Rate.builder().ask(101).bid(95).timestamp(LocalDateTime.now().minusMinutes(2)).build());
        rateAnalyzer.add(Rate.builder().ask(301).bid(300).timestamp(LocalDateTime.now().minusMinutes(1)).build());
        Assertions.assertTrue(rateAnalyzer.isUpwardWithin(Duration.ofMinutes(10)));
    }

}
