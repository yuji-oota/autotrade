package autotrade.local.actor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;
import lombok.extern.slf4j.Slf4j;

@ActiveProfiles("test")
@SpringBootTest
@Slf4j
class RangeManagerTest {

    @Autowired
    RangeManager rangeManager;
    @Autowired
    RangeManager rangeManager2;

    @Autowired
    PairManager pairManager;

    @Test
    void test() {
        rangeManager.save(Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("USDJPY"))
                        .rawAsk("110").rawBid("100")
                        .ask(110).bid(100).build())
                .build());
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());
        rangeManager.apply();
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());

        rangeManager.save(Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("USDJPY"))
                        .rawAsk("105").rawBid("100")
                        .ask(105).bid(100).build())
                .build());
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());
        rangeManager.apply();
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());

        rangeManager.save(Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("USDJPY"))
                        .rawAsk("105").rawBid("99")
                        .ask(105).bid(99).build())
                .build());
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());
        rangeManager.apply();
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());

        rangeManager.save(Snapshot.builder()
                .rate(Rate.builder().pair(pairManager.get("USDJPY"))
                        .rawAsk("105").rawBid("90")
                        .ask(105).bid(90).build())
                .build());
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());
        rangeManager.apply();
        log.info("isSaveExtend:{} isExtended:{}", rangeManager.isSaveExtend(), rangeManager.isExtended());

    }

    @Test
    void test2() {
        System.out.println(rangeManager);
        System.out.println(rangeManager2);
    }

}
