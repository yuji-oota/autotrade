package autotrade.local.material;

import org.junit.jupiter.api.Test;

class PairTest {

    @Test
    void test() {
        Pair pairA = Pair.builder().name("A").build();
        Pair pairB = Pair.builder().name("B").build();
        Pair pairC = Pair.builder().name("A").build();
        System.out.println(pairA.equals(pairB));
        System.out.println(pairA.equals(pairC));
    }

}
