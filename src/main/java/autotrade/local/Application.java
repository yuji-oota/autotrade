package autotrade.local;

import autotrade.local.trader.AutoTrader;

public class Application {

    public static void main(String[] args) {

        while(true) {
            AutoTrader.getInstance().operation();
        }

    }

}
