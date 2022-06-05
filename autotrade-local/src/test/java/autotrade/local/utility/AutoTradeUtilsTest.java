package autotrade.local.utility;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class AutoTradeUtilsTest {

    @Test
    public void test() {
        System.out.println("aaa");
        System.out.println(AutoTradeUtils.nextNiceRound(5));
        System.out.println(AutoTradeUtils.nextNiceRound(10));
        System.out.println(AutoTradeUtils.nextNiceRound(15));
        System.out.println(AutoTradeUtils.nextNiceRound(30));
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
