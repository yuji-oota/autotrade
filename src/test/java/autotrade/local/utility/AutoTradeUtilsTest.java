package autotrade.local.utility;


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

}
