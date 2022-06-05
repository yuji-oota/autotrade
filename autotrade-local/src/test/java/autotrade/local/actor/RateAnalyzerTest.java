package autotrade.local.actor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
public class RateAnalyzerTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    public void test() {
        RateAnalyzer rateAnalyzer = applicationContext.getBean(RateAnalyzer.class);
        System.out.println(rateAnalyzer.getRatesDuration());
    }

}
