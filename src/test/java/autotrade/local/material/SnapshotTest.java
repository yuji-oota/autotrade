package autotrade.local.material;

import org.junit.jupiter.api.Test;

class SnapshotTest {

    @Test
    void test() {
        Snapshot org = Snapshot.builder().askLot(100).bidLot(200).rate(Rate.builder().build()).build();
        Snapshot clone = org.toBuilder().askLot(300).build();
        System.out.println(org);
        System.out.println(clone);
    }
}
