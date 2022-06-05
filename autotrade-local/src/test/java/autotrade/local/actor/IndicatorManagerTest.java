package autotrade.local.actor;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import autotrade.local.utility.WebDriverWrapper;

@ActiveProfiles("test")
@SpringBootTest
class IndicatorManagerTest {

    @Autowired
    IndicatorManager indicatorManager;

    @Autowired
    protected WebDriverWrapper webDriverWrapper;
    
    @Test
    void test() {
        webDriverWrapper.setDriver(new ChromeDriver());
        indicatorManager.addIndicators(webDriverWrapper.getIndicators());
        System.out.println(indicatorManager.isPrevImportant());
    }

}
