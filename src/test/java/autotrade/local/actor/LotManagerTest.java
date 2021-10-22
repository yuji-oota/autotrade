package autotrade.local.actor;

import org.junit.jupiter.api.Test;

import autotrade.local.material.Snapshot;

public class LotManagerTest {

    @Test
    public void test() {
        LotManager lotManager = new LotManager();
        Snapshot snapshot = null;
        snapshot = Snapshot.builder().margin(300000).bidLot(0).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(300000).bidLot(1).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(300000).bidLot(2).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(300000).bidLot(3).build();
        System.out.println(lotManager.nextLot(snapshot));

        System.out.println("");

        snapshot = Snapshot.builder().margin(1000000).askLot(1).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(1000000).askLot(3).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(1000000).askLot(5).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(1000000).askLot(7).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(1000000).askLot(9).build();
        System.out.println(lotManager.nextLot(snapshot));
        snapshot = Snapshot.builder().margin(1000000).askLot(11).build();
        System.out.println(lotManager.nextLot(snapshot));
    }
}
