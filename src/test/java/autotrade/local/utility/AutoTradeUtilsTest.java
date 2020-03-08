package autotrade.local.utility;

import org.junit.Test;

import autotrade.local.material.AudioPath;

public class AutoTradeUtilsTest {

    @Test
    public void test() {
        AutoTradeUtils.playAudioRandom(AudioPath.FixSoundEffect);
    }

}
