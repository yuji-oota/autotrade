package autotrade.local.actor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class RecoveryManagerTest {

    @Autowired
    RecoveryManager recoveryManager;

    @Test
    void test() {
    }

}
