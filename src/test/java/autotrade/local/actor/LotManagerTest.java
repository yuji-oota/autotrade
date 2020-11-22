package autotrade.local.actor;

import org.junit.jupiter.api.Test;

import autotrade.local.material.CurrencyPair;

public class LotManagerTest {

    @Test
    public void test() {
        LotManager lotManager = new LotManager();
        lotManager.changePair(CurrencyPair.EURUSD, 100000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 200000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 300000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 400000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 500000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 600000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 700000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 800000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 900000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 1000000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 1500000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
        lotManager.changePair(CurrencyPair.EURUSD, 2000000);
        System.out.println(lotManager.getInitial() + ", " + lotManager.getLimit());
    }
}
