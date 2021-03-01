package autotrade.local.trader;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import autotrade.local.utility.AwsS3ClientWrapper;

public class AwsS3ClientWrapperTest {

    @Test
    public void test() {
        AwsS3ClientWrapper.upload(Paths.get("log", "autotrade-local.log"));
    }

}
