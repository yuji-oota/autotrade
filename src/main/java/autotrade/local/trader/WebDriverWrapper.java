package autotrade.local.trader;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebDriverWrapper {

    private WebDriver driver;

    public WebDriverWrapper(WebDriver driver) {
        this.driver = driver;
    }

    public void login() {
        driver.get("https://trade.fx.dmm.com/comportal/Login.do?type=1");
        driver.findElement(By.id("username")).sendKeys(AutoTradeProperties.get("login.username"));
        driver.findElement(By.id("passwordShow")).sendKeys(AutoTradeProperties.get("login.password"));
        driver.findElement(By.id("LoginWindowBtn")).click();
    }
    public void startUpTradeTool() {
        driver.navigate().to("https://trade.fx.dmm.com/comportal/SsoOutbound.do?subSystemType=-20");
    }
    public String getAskLot() {
        return driver.findElement(By.xpath("//span[@uifield='askTotalAmount']")).getText();
    }
    public String getBidLot() {
        return driver.findElement(By.xpath("//span[@uifield='bidTotalAmount']")).getText();
    }
    public String getProfit() {
        return driver.findElement(By.xpath("//span[@uifield='balancePl']")).getText();
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
        driver.findElement(By.xpath("//input[@uifield='orderQuantity']")).sendKeys(String.valueOf(lot));
        log.info("lot {}", lot);
    }
    public void orderAsk() {
        driver.findElement(By.xpath("//div[@uifield='bidStreamingButton']")).click();
        log.info("ask {}", getAskRate());
    }
    public void orderBid() {
        driver.findElement(By.xpath("//div[@uifield='askStreamingButton']")).click();
        log.info("bid {}", getBidRate());
    }
    public void fixProfit() {
        driver.findElement(By.xpath("//button[@uifield='orderButtonAll']")).click();
        log.info("profit {}", getProfit());
    }
}
