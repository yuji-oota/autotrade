package autotrade.local.utility;


import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import autotrade.local.material.AudioPath;

public class AutoTradeUtilsTest {

    @Test
    public void test() {
        IntStream.of(0,1,2).forEach(i -> {
            AutoTradeUtils.playAudioRandom(AudioPath.Alert);
        });
        AutoTradeUtils.sleep(Duration.ofSeconds(10));

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
