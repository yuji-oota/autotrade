package autotrade.local.actor;

import org.junit.jupiter.api.Test;

class PairManagerTest {

    @Test
    void test() {
        PairManager pairManager = new PairManager("USDJPY");
        System.out.println(pairManager.getMinSpread());
        System.out.println(pairManager.getMarginRequirement());
    }

}
