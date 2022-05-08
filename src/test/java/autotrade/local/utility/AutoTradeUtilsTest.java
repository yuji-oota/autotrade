package autotrade.local.utility;


import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class AutoTradeUtilsTest {

    @Test
    public void test() {
        System.out.println(AutoTradeUtils.isNumeric(""));
        System.out.println(AutoTradeUtils.isNumeric("+"));
        System.out.println(AutoTradeUtils.isNumeric("-"));
        System.out.println(AutoTradeUtils.isNumeric("1"));
        System.out.println(AutoTradeUtils.isNumeric("10"));
        System.out.println(AutoTradeUtils.isNumeric("100"));
        System.out.println(AutoTradeUtils.isNumeric("100."));
        System.out.println(AutoTradeUtils.isNumeric("100.0"));
        System.out.println(AutoTradeUtils.isNumeric("100.01"));
        System.out.println(AutoTradeUtils.isNumeric("100.001"));
        System.out.println(AutoTradeUtils.isNumeric("+1"));
        System.out.println(AutoTradeUtils.isNumeric("-10"));
        System.out.println(AutoTradeUtils.isNumeric("+100"));
        System.out.println(AutoTradeUtils.isNumeric("-100."));
        System.out.println(AutoTradeUtils.isNumeric("+100.0"));
        System.out.println(AutoTradeUtils.isNumeric("-100.01"));
        System.out.println(AutoTradeUtils.isNumeric("+100.001"));
        System.out.println(AutoTradeUtils.isNumeric("+a"));
        System.out.println(AutoTradeUtils.isNumeric("+1a"));
        System.out.println(AutoTradeUtils.isNumeric("+10a"));
        System.out.println(AutoTradeUtils.isNumeric("+100."));
        System.out.println(AutoTradeUtils.isNumeric("+100.a"));
        System.out.println(AutoTradeUtils.isNumeric("+100.0a"));
        System.out.println(AutoTradeUtils.isNumeric("+100.00a"));
    }

    @Test
    public void save() {
//        Snapshot snapshot = Snapshot.builder().rate(Rate.builder().ask(101).bid(-101).build()).askLot(50).bidLot(-50).build();
//        RecoveryManager recoveryManager = new RecoveryManager();
//        recoveryManager.open(snapshot);
//        AutoTradeUtils.localSave(Paths.get("localSave", "recoveryManager"), recoveryManager);
    }

    @Test
    public void load() {
        int stopLossRate = AutoTradeUtils.localLoad(Paths.get("localSave", "stopLossRate"));
        System.out.println(stopLossRate);
    }


}
