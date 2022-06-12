package autotrade.local.actor;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import autotrade.local.utility.AutoTradeUtils;

@ActiveProfiles("test")
@SpringBootTest
class JmsMessageListenerTest {

    @Autowired
    JmsMessageListener jmsMessageListener;
    
    @Test
    void test() {
        jmsMessageListener.addHandler("key1", s -> System.out.println("key1 execute"));
        jmsMessageListener.addHandler("key2", s -> System.out.println("key2 execute"));
        AutoTradeUtils.sleep(Duration.ofMinutes(60));
    }

}
