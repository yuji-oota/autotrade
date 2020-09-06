package autotrade.local.actor;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import autotrade.local.material.Rate;
import autotrade.local.utility.AutoTradeUtils;

public class RateAnalyzerTest {

    @Test
    public void test() {
        RateAnalyzer rateAnalyzer = new RateAnalyzer();
        rateAnalyzer.add(Rate.builder().ask(100100).bid(100000).timestamp(LocalDateTime.now()).build());
        AutoTradeUtils.printObject(rateAnalyzer.getRatioThresholdAsk());
        AutoTradeUtils.printObject(rateAnalyzer.getRatioThresholdBid());
        AutoTradeUtils.printObject(rateAnalyzer.getMiddleThreshold());
    }

}
