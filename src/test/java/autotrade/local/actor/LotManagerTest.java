package autotrade.local.actor;

import org.junit.Test;

import autotrade.local.material.Snapshot;

public class LotManagerTest {

    @Test
    public void test() {
        LotManager lotManager = new LotManager();
        Snapshot snapshot = Snapshot.builder().build();
        int next;

        System.out.println("ask:" + snapshot.getAskLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setAskLot(next + snapshot.getAskLot());

        System.out.println("bid:" + snapshot.getBidLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setBidLot(next + snapshot.getBidLot());

        System.out.println("ask:" + snapshot.getAskLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setAskLot(next + snapshot.getAskLot());

        System.out.println("bid:" + snapshot.getBidLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setBidLot(next + snapshot.getBidLot());

        System.out.println("ask:" + snapshot.getAskLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setAskLot(next + snapshot.getAskLot());

        System.out.println("bid:" + snapshot.getBidLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setBidLot(next + snapshot.getBidLot());

        System.out.println("ask:" + snapshot.getAskLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setAskLot(next + snapshot.getAskLot());

        System.out.println("bid:" + snapshot.getBidLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setBidLot(next + snapshot.getBidLot());

        System.out.println("ask:" + snapshot.getAskLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setAskLot(next + snapshot.getAskLot());

        System.out.println("bid:" + snapshot.getBidLot());
        next = lotManager.nextLot(snapshot);
        System.out.println(next);
        snapshot.setBidLot(next + snapshot.getBidLot());
    }

}
