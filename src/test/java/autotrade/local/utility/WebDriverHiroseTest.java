package autotrade.local.utility;

import java.text.MessageFormat;

import org.junit.jupiter.api.Test;


public class WebDriverHiroseTest {

    @Test
    public void test() {
        System.out.println(MessageFormat.format("//tr[@id=''tab_rate_brand_{0}'']//span[starts-with(@class, ''ask'')]", "USDJPY"));
    }
}
