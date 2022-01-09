package autotrade.local.config;

import java.util.Map;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import autotrade.local.actor.PairManager;
import autotrade.local.actor.RateAnalyzer;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;

@ActiveProfiles("test")
@SpringBootTest
class AutoTradeConfigTest {

    @Autowired
    ToIntFunction<Snapshot> toProfit;

    @Autowired
    ToIntFunction<Snapshot> toInitialLot;

    @Autowired
    ToIntFunction<Snapshot> toMinimumProfit;

    @Autowired
    ToIntFunction<Snapshot> toTargetProgress;

    @Autowired
    PairManager pairManager;

    @Autowired
    @Qualifier("pairAnalyzerMap")
    Map<String, RateAnalyzer> pairAnalyzerMap;

    @Test
    void test() {
        Snapshot snapshot = Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("USDJPY")).build())
                .margin(100000)
                .build();

        System.out.println(toProfit.applyAsInt(snapshot));
        System.out.println(toInitialLot.applyAsInt(snapshot));
        System.out.println(toMinimumProfit.applyAsInt(snapshot));
        System.out.println(toTargetProgress.applyAsInt(snapshot));
        System.out.println(pairAnalyzerMap.size());
        System.out.println(pairAnalyzerMap.get("USDJPY"));
        System.out.println(pairAnalyzerMap.get("EURUSD"));
        ;
    }

}
