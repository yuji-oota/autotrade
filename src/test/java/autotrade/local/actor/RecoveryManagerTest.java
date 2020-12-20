package autotrade.local.actor;

import org.junit.jupiter.api.Test;

import autotrade.local.material.Snapshot;

class RecoveryManagerTest {

    @Test
    void test() {
        RecoveryManager recoveryManager = new RecoveryManager();
        recoveryManager.open(Snapshot.builder().build());
        System.out.println(recoveryManager.isAfterCounterTrading());
        recoveryManager.setCounterTradingSnapshot(Snapshot.builder().askLot(1).build());
        System.out.println(recoveryManager.isAfterCounterTrading());
    }

}
