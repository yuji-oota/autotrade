package autotrade.local.utility;

import java.text.MessageFormat;
import java.time.Duration;
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

import autotrade.local.material.CurrencyPair;

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
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        String currentWindow = driver.getWindowHandle();
        driver.findElement(By.xpath("//a[@href='https://lionfx.hirose-fx.co.jp/WTChartWeb/index.html']")).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(5));

        driver.switchTo().window(driver.getWindowHandles().stream()
                .filter(window -> !window.equals(currentWindow))
                .findFirst()
                .orElse(currentWindow)
                );
        driver.findElement(By.id("login-id")).sendKeys(AutoTradeProperties.get("fx.login.username"));
        driver.findElement(By.id("login-pw")).sendKeys(AutoTradeProperties.get("fx.login.password"));
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        driver.findElement(By.id("doLogin")).click();
    }
    public void cancelMessage() {
        try {
            driver.findElement(By.xpath("//button[starts-with(@id, 'message-cancel')]")).click();
        } catch (Exception e) {
            // 何もしない
        }
    }
    public void startUpTradeTool() {
//        driver.navigate().to("https://trade.fx.dmm.com/comportal/SsoOutbound.do?subSystemType=-20");
        driver.manage().window().maximize();
    }
    public void orderSettings() {
        driver.findElement(By.id("fifo-on-label-quick")).click();
        driver.findElement(By.id("all-confm-quick")).click();
        driver.findElement(By.id("account-status")).click();
    }
    public void pairSettings() {
        // 通貨ペア設定
        // ヒロセではここで設定した通貨ペアに変更可能
        driver.findElement(By.id("brand-menu-button")).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        driver.findElement(By.id("brand-regist-nocheck")).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        List<String> pairs = CurrencyPair.getDescriptions();
        List<WebElement> elements = driver.findElements(By.xpath("//li[@class='brand-regist-list-item']"));
        elements.stream().filter(e -> {
            for (String pair : pairs) {
                if (!e.findElements(By.xpath(MessageFormat.format("div[contains(text(),\"{0}\")]", pair))).isEmpty()) {
                    return true;
                }
            }
            return false;
        })
        .forEach(e -> e.click());
        driver.findElement(By.id("brand-regist-ok")).click();
    }

    public String getPair() {
        return driver.findElement(By.id("order-brand")).getText();
    }
    public String getMargin() {
        return driver.findElement(By.xpath("//div[@id='account-status-01-value']")).getText();
    }
    public String getAskLot() {
        return driver.findElement(By.xpath("//div[@id='order-quick']/div[3]/div[2]/div/div[3]")).getText().replace("　(0)", "");
    }
    public String getBidLot() {
        return driver.findElement(By.xpath("//div[@id='order-quick']/div[3]/div[2]/div/div[1]")).getText().replace("　(0)", "");
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
        }
    }
    public void orderAsk() {
        try {
            driver.findElement(By.id("buy-panel-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void orderBid() {
        try {
            driver.findElement(By.id("sell-panel-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void fixAll() {
        try {
            driver.findElement(By.id("brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void fixAsk() {
        try {
            driver.findElement(By.id("buy-brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void fixBid() {
        try {
            driver.findElement(By.id("sell-brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void changePair(String pair) {
        driver.findElement(By.xpath("//li[@aria-controls='rate-menu']")).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        driver.findElement(By.xpath(
                MessageFormat.format("//div[contains(text(),\"{0}\")]", pair))).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        driver.findElement(By.xpath("//li[@aria-controls='chart-menu']")).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        orderSettings();
    }

}
