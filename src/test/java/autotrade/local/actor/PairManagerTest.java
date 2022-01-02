package autotrade.local.actor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class PairManagerTest {

    @Autowired
    PairManager pairManager;

    @Test
    void test() {
        pairManager.getPairs().forEach(System.out::println);
    }

}
