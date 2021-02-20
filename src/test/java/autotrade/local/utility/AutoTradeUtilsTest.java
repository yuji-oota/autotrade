package autotrade.local.utility;


import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import autotrade.local.material.AudioPath;
import autotrade.local.material.Rate;
import autotrade.local.material.Snapshot;

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
        Snapshot snapshot = Snapshot.builder().rate(Rate.builder().ask(101).bid(-101).build()).askLot(50).bidLot(-50).build();
        AutoTradeUtils.localSave(Paths.get("localSave", "snapshot"), snapshot);
    }

    @Test
    public void load() {
        Snapshot snapshot = AutoTradeUtils.localLoad(Paths.get("localSave", "snapshot"));
        AutoTradeUtils.printObject(snapshot);
    }


}
