package autotrade.local.trader;

import java.nio.file.Paths;

import org.junit.Test;

public class AwsS3ClientWrapperTest {

    @Test
    public void test() {
        AwsS3ClientWrapper.upload(Paths.get("log", "autotrade-local.log"));
    }

}
