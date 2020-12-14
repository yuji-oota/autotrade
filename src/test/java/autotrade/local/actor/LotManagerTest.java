package autotrade.local.actor;

import org.junit.jupiter.api.Test;

import autotrade.local.material.CurrencyPair;

public class LotManagerTest {

    @Test
    public void test() {
        LotManager lotManager = new LotManager();
        int margin = 0;
        margin = 100000;
        lotManager.changePair(CurrencyPair.EURUSD, margin);
        System.out.println("margin:" + margin + ", " + lotManager.getInitial() + ", " + lotManager.getLimit());
        margin = 300000;
        lotManager.changePair(CurrencyPair.EURUSD, margin);
        System.out.println("margin:" + margin + ", " + lotManager.getInitial() + ", " + lotManager.getLimit());
        margin = 350000;
        lotManager.changePair(CurrencyPair.EURUSD, margin);
        System.out.println("margin:" + margin + ", " + lotManager.getInitial() + ", " + lotManager.getLimit());
        margin = 360000;
        lotManager.changePair(CurrencyPair.EURUSD, margin);
        System.out.println("margin:" + margin + ", " + lotManager.getInitial() + ", " + lotManager.getLimit());
    }
}
