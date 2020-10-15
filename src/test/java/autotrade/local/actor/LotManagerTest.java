package autotrade.local.actor;

import org.junit.jupiter.api.Test;

import autotrade.local.material.CurrencyPair;

public class LotManagerTest {

    @Test
    public void test() {
        LotManager lotManager = new LotManager();
//        Snapshot snapshot = Snapshot.builder().build();
//        int next;
//
//        System.out.println("ask:" + snapshot.getAskLot());
//        System.out.println("bid:" + snapshot.getBidLot());
//        next = lotManager.nextLot(snapshot);
//        System.out.println("next ask:" + next);
//        snapshot.setAskLot(next + snapshot.getAskLot());
//
//        System.out.println("ask:" + snapshot.getAskLot());
//        System.out.println("bid:" + snapshot.getBidLot());
//        next = lotManager.nextLot(snapshot);
//        System.out.println("next bid:" + next);
//        snapshot.setBidLot(next + snapshot.getBidLot());
//
//        System.out.println("ask:" + snapshot.getAskLot());
//        System.out.println("bid:" + snapshot.getBidLot());
//        next = lotManager.nextLot(snapshot);
//        System.out.println("next ask:" + next);
//        snapshot.setAskLot(next + snapshot.getAskLot());
//
//        System.out.println("ask:" + snapshot.getAskLot());
//        System.out.println("bid:" + snapshot.getBidLot());
//        next = lotManager.nextLot(snapshot);
//        System.out.println("next bid:" + next);
//        snapshot.setBidLot(next + snapshot.getBidLot());
//
//        System.out.println("ask:" + snapshot.getAskLot());
//        System.out.println("bid:" + snapshot.getBidLot());
//        next = lotManager.nextLot(snapshot);
//        System.out.println("next ask:" + next);
//        snapshot.setAskLot(next + snapshot.getAskLot());
//
//        System.out.println("ask:" + snapshot.getAskLot());
//        System.out.println("bid:" + snapshot.getBidLot());
//        next = lotManager.nextLot(snapshot);
//        System.out.println("next bid:" + next);
//        snapshot.setBidLot(next + snapshot.getBidLot());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 900000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1000000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1100000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1200000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1300000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1400000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1500000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1600000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1700000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1800000);
        System.out.println(lotManager.getInitial());
        lotManager.changeInitialLot(CurrencyPair.EURUSD, 1900000);
        System.out.println(lotManager.getInitial());
    }
}
