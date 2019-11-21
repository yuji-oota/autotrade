package autotrade.local.utility;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
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
        driver.get("https://trade.fx.dmm.com/comportal/Login.do?type=1");
        driver.findElement(By.id("username")).sendKeys(AutoTradeProperties.get("login.username"));
        driver.findElement(By.id("passwordShow")).sendKeys(AutoTradeProperties.get("login.password"));
        driver.findElement(By.id("LoginWindowBtn")).click();
    }
    public void startUpTradeTool() {
        driver.navigate().to("https://trade.fx.dmm.com/comportal/SsoOutbound.do?subSystemType=-20");
        driver.manage().window().maximize();
    }
    public void settings() {
        driver.findElement(By.xpath("//input[@uifield='orderSettings']")).click();
        AutoTradeUtils.sleep(1000);

        driver.findElement(By.xpath("//div[contains(text(),\"その他設定\")]")).click();
        driver.findElement(By.xpath("//input[@uifield='displayGroupSettlementConfirmLayerFlag']")).click();
        driver.findElement(By.xpath("//input[@uifield='displayGroupSettlementResultLayerFlag']")).click();

        JavascriptExecutor jexec = (JavascriptExecutor) driver;
        jexec.executeScript("document.querySelector('div.orderSettings').style.display='none';");
        jexec.executeScript("document.getElementById('disableLayer').style.width=0;");
    }
    public String getAskLot() {
        return driver.findElement(By.xpath("//span[@uifield='askTotalAmount']")).getText();
    }
    public String getBidLot() {
        return driver.findElement(By.xpath("//span[@uifield='bidTotalAmount']")).getText();
    }
    public String getAskAverageRate() {
        return driver.findElement(By.xpath("//span[@uifield='askAvgExecutionPrice']")).getText();
    }
    public String getBidAverageRate() {
        return driver.findElement(By.xpath("//span[@uifield='bidAvgExecutionPrice']")).getText();
    }
    public String getAskProfit() {
        return driver.findElement(By.xpath("//span[@uifield='askEvaluationPl']")).getText();
    }
    public String getBidProfit() {
        return driver.findElement(By.xpath("//span[@uifield='bidEvaluationPl']")).getText();
    }
    public String getTodaysProfit() {
        return driver.findElement(By.xpath("//span[@uifield='dailyPlTotalJPY']")).getText();
    }
    public String getBidRate() {
        return driver.findElement(By.xpath("//div[@uifield='bidStreamingButton']/div/div[@class='small']")).getText()
                + driver.findElement(By.xpath("//div[@uifield='bidStreamingButton']/div/div[@class='big']")).getText()
                + driver.findElement(By.xpath("//div[@uifield='bidStreamingButton']/div/div[@class='fraction']")).getText();
    }
    public String getAskRate() {
        return driver.findElement(By.xpath("//div[@uifield='askStreamingButton']/div/div[@class='small']")).getText()
                + driver.findElement(By.xpath("//div[@uifield='askStreamingButton']/div/div[@class='big']")).getText()
                + driver.findElement(By.xpath("//div[@uifield='askStreamingButton']/div/div[@class='fraction']")).getText();
    }
    public void setLot(int lot) {
        String lastLot = driver.findElement(By.xpath("//input[@uifield='orderQuantity']")).getAttribute("value");
        if (!lastLot.equals(String.valueOf(lot))) {
            driver.findElement(By.xpath("//input[@uifield='orderQuantity']")).sendKeys(Keys.chord(Keys.CONTROL,"a"));
            driver.findElement(By.xpath("//input[@uifield='orderQuantity']")).sendKeys(String.valueOf(lot));
            log.info("lot {}", lot);
        }

    }
    public void orderAsk() {
        driver.findElement(By.xpath("//div[@uifield='askStreamingButton']")).click();
        log.info("ask {}", getAskRate());
    }
    public void orderBid() {
        driver.findElement(By.xpath("//div[@uifield='bidStreamingButton']")).click();
        log.info("bid {}", getBidRate());
    }
    public void fixAll() {
        driver.findElement(By.xpath("//button[@uifield='orderButtonAll']")).click();
    }
    public void fixAsk() {
        driver.findElement(By.xpath("//button[@uifield='orderButtonBuy']")).click();
    }
    public void fixBid() {
        driver.findElement(By.xpath("//button[@uifield='orderButtonSell']")).click();
    }

}
