package autotrade.local.utility;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebDriverWrapper {

    private WebDriver driver;

    public WebDriverWrapper(WebDriver driver) {
        this.driver = driver;
    }
    public List<LocalDateTime> getIndicators(LocalDate targetDate) {
        driver.get("https://fx.dmm.com/market/indicators/");
        List<WebElement> elements = driver.findElements(By.xpath(
                MessageFormat.format("//td[contains(text(),\"{0}月{1}日\")]", targetDate.getMonthValue(), targetDate.getDayOfMonth())));
        return elements.stream()
                .map(WebElement::getText)
                .distinct()
                .map(s -> {
                    String time = s.substring(s.length() - 5);
                    return LocalDateTime.of(targetDate, LocalTime.from(DateTimeFormatter.ISO_TIME.parse(time)));
                })
                .collect(Collectors.toList());
    }
    public void login() {

        driver.get("https://hirose-fx.co.jp/landing/tool_lp/#web");
        AutoTradeUtils.sleep(1000);

        String currentWindow = driver.getWindowHandle();
        driver.findElement(By.xpath("//a[@href='https://lionfx.hirose-fx.co.jp/WTChartWeb/index.html']")).click();
        AutoTradeUtils.sleep(15000);

        driver.switchTo().window(driver.getWindowHandles().stream()
                .filter(window -> !window.equals(currentWindow))
                .findFirst()
                .orElse(currentWindow)
                );
        driver.findElement(By.id("login-id")).sendKeys(AutoTradeProperties.get("fx.login.username"));
        driver.findElement(By.id("login-pw")).sendKeys(AutoTradeProperties.get("fx.login.password"));
        AutoTradeUtils.sleep(1000);
        driver.findElement(By.id("doLogin")).click();
    }
    public void startUpTradeTool() {
//        driver.navigate().to("https://trade.fx.dmm.com/comportal/SsoOutbound.do?subSystemType=-20");
        driver.manage().window().maximize();
    }
    public void settings() {
        driver.findElement(By.id("fifo-on-label-quick")).click();
        driver.findElement(By.id("all-confm-quick")).click();
        driver.findElement(By.id("account-status")).click();

//        driver.findElement(By.xpath("//input[@uifield='orderSettings']")).click();
//        AutoTradeUtils.sleep(1000);
//
//        driver.findElement(By.xpath("//div[contains(text(),\"その他設定\")]")).click();
//        driver.findElement(By.xpath("//input[@uifield='displayGroupSettlementConfirmLayerFlag']")).click();
//        driver.findElement(By.xpath("//input[@uifield='displayGroupSettlementResultLayerFlag']")).click();
//
//        JavascriptExecutor jexec = (JavascriptExecutor) driver;
//        jexec.executeScript("document.querySelector('div.orderSettings').style.display='none';");
//        jexec.executeScript("document.getElementById('disableLayer').style.width=0;");
    }
    public String getMargin() {
        return driver.findElement(By.xpath("//div[@id='account-status-01-value']")).getText();
    }
    public String getAskLot() {
        return driver.findElement(By.xpath("//div[@id='order-quick']/div[3]/div[2]/div/div[3]")).getText();
    }
    public String getBidLot() {
        return driver.findElement(By.xpath("//div[@id='order-quick']/div[3]/div[2]/div/div[1]")).getText();
    }
    public String getAskAverageRate() {
        return driver.findElement(By.xpath("//div[@class='buy-avg-rate total-row-base']")).getText();
    }
    public String getBidAverageRate() {
        return driver.findElement(By.xpath("//div[@class='sell-avg-rate total-row-base']")).getText();
    }
//    public String getAskProfit() {
//        return driver.findElement(By.xpath("//span[@uifield='askEvaluationPl']")).getText();
//    }
//    public String getBidProfit() {
//        return driver.findElement(By.xpath("//span[@uifield='bidEvaluationPl']")).getText();
//    }
    public String getAskPipProfit() {
        return driver.findElement(By.xpath("//div[@class='buy-pip-profit total-row-base']")).getText();
    }
    public String getBidPipProfit() {
        return driver.findElement(By.xpath("//div[@class='sell-pip-profit total-row-base']")).getText();
    }
//    public String getTodaysProfit() {
//        return driver.findElement(By.xpath("//span[@uifield='dailyPlTotalJPY']")).getText();
//    }
    public String getBidRate() {
        return driver.findElement(By.xpath("//span[@class='bid1']")).getText()
                + driver.findElement(By.xpath("//span[@class='bid2']")).getText()
                + driver.findElement(By.xpath("//span[@class='bid3']")).getText();
    }
    public String getAskRate() {
        return driver.findElement(By.xpath("//span[@class='ask1']")).getText()
                + driver.findElement(By.xpath("//span[@class='ask2']")).getText()
                + driver.findElement(By.xpath("//span[@class='ask3']")).getText();
    }
    public void setLot(int lot) {
        String lastLot = driver.findElement(By.id("lot-param-quick-val")).getAttribute("value");
        if (!lastLot.equals(String.valueOf(lot))) {
            driver.findElement(By.id("lot-param-quick-val")).sendKeys(Keys.chord(Keys.CONTROL,"a"));
            driver.findElement(By.id("lot-param-quick-val")).sendKeys(String.valueOf(lot));
            log.info("lot {}", lot);
        }
    }
    public void orderAsk() {
        try {
            driver.findElement(By.id("buy-panel-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("ask {}", getAskRate());
        AutoTradeUtils.sleep(3000);
    }
    public void orderBid() {
        try {
            driver.findElement(By.id("sell-panel-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("bid {}", getBidRate());
        AutoTradeUtils.sleep(3000);
    }
    public void fixAll() {
        try {
            driver.findElement(By.id("brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
        AutoTradeUtils.sleep(1500);
    }
    public void fixAsk() {
        try {
            driver.findElement(By.id("buy-brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
        AutoTradeUtils.sleep(1500);
    }
    public void fixBid() {
        try {
            driver.findElement(By.id("sell-brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
        AutoTradeUtils.sleep(1500);
    }

}
