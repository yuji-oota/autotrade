package autotrade.local.utility;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import autotrade.local.material.Indicator;

public class WebDriverHiroseTest {

    @Test
    public void test() {
        WebDriver driver = new ChromeDriver();
        WebDriverWrapper wrapper = new WebDriverHirose(driver);
        wrapper.getIndicators().forEach(Indicator::print);
        driver.quit();
    }
}
