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
    ToIntFunction<Snapshot> toNextLot;

    @Autowired
    ToIntFunction<Snapshot> toMinimumProfit;

    @Autowired
    PairManager pairManager;

    @Autowired
    @Qualifier("pairAnalyzerMap")
    Map<String, RateAnalyzer> pairAnalyzerMap;

    @Test
    void test() {
        Snapshot snapshot = Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("USDJPY")).build())
                .margin(3000000)
                .effectiveMargin(3000000)
                .askLot(58)
                .build();

        System.out.println(toProfit.applyAsInt(snapshot));
        System.out.println(toInitialLot.applyAsInt(snapshot));
        System.out.println(snapshot.getLimitLot());
        System.out.println(snapshot.getMoreLot());
        System.out.println(toNextLot.applyAsInt(snapshot));
        System.out.println(toMinimumProfit.applyAsInt(snapshot));
        System.out.println(pairAnalyzerMap.size());
        System.out.println(pairAnalyzerMap.get("USDJPY"));
        System.out.println(pairAnalyzerMap.get("EURUSD"));
        ;

        snapshot = Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("USDJPY")).build())
                .margin(30000)
                .effectiveMargin(30000)
                .askLot(0)
                .build();
        System.out.println(toNextLot.applyAsInt(snapshot));
    }

    @Test
    void test2() {
        Snapshot snapshot = Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("AUDJPY")).build())
                .askLot(0)
                .build();
        snapshot.setMargin(100000);
        System.out.println(snapshot.getLimitLot() + " " + toInitialLot.applyAsInt(snapshot));
        snapshot.setMargin(500000);
        System.out.println(snapshot.getLimitLot() + " " + toInitialLot.applyAsInt(snapshot));
        snapshot.setMargin(900000);
        System.out.println(snapshot.getLimitLot() + " " + toInitialLot.applyAsInt(snapshot));
        snapshot.setMargin(1000000);
        System.out.println(snapshot.getLimitLot() + " " + toInitialLot.applyAsInt(snapshot));
        snapshot.setMargin(2000000);
        System.out.println(snapshot.getLimitLot() + " " + toInitialLot.applyAsInt(snapshot));
        snapshot.setMargin(3000000);
        System.out.println(snapshot.getLimitLot() + " " + toInitialLot.applyAsInt(snapshot));
    }

}
