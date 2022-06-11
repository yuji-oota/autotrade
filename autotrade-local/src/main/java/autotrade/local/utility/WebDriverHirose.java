package autotrade.local.utility;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.openqa.selenium.By;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import autotrade.local.actor.PairManager;
import autotrade.local.material.Indicator;
import autotrade.local.material.Pair;
import lombok.extern.slf4j.Slf4j;

@Component("webDriverHirose")
@Slf4j
public class WebDriverHirose implements WebDriverWrapper {

    private WebDriver driver;

    @Value("${fx.login.username}")
    private String username;

    @Value("${fx.login.password}")
    private String password;

    @Autowired
    private PairManager pairManager;

    @Override
    public void setDriver(WebDriver driver) {
        this.driver = driver;
    }

    @Override
    public List<Indicator> getIndicators() {
        driver.get("https://hirose-fx.co.jp/hrsphpapl/mnavi/mnavi_keizaishihyo.php?stdate=");
        WebElement iframe = driver.findElement(By.name("SUBSCREEN"));
        driver.switchTo().frame(iframe);

        WebElement rootElement = driver.findElement(By.xpath("/html/body/table/tbody"));
        List<WebElement> trs = rootElement.findElements(By.tagName("tr"));
        List<Indicator> indicators = trs.stream()
                .map(tr -> {
                    List<WebElement> tds = tr.findElements(By.tagName("td"));
                    Indicator indicator = new Indicator();
                    indicator.setRawDate(tds.get(0).getText().replace("\n", ""));
                    indicator.setRawTime(tds.get(1).getText());
                    indicator.setCountryName(tds.get(2).findElements(By.tagName("img")).get(0).getAttribute("title"));
                    indicator.setIndicatorName(tds.get(3).getText());
                    indicator.setImportant(!tr.findElements(By.className("r1")).isEmpty());
                    return indicator;
                })
                .collect(Collectors.toList());

        // Indicator.dateTimeを補完
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withResolverStyle(ResolverStyle.LENIENT);
        String isoDate = null;
        for (Indicator indicator : indicators) {
            if (!indicator.getRawDate().isEmpty()) {
                isoDate = LocalDate.now().getYear() + "-"
                        + indicator.getRawDate().replaceAll("\\(.*", "").replace("/", "-");
            }
            if ("*".equals(indicator.getRawTime())) {
                continue;
            }
            indicator.setDateTime(LocalDateTime.parse(isoDate + "T" + indicator.getRawTime(), formatter));
        }
        return indicators.stream()
                .filter(ind -> Objects.nonNull(ind.getDateTime()))
                .filter(ind -> LocalDateTime.now().isBefore(ind.getDateTime()))
                .filter(ind -> !"*".equals(ind.getRawTime()))
                .toList();
    }

    @Override
    public void initialize() {
        // ログイン
        login();
        AutoTradeUtils.sleep(Duration.ofSeconds(5));

        // メッセージダイアログクローズ
        cancelMessage();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        // ツール起動
        startUpTradeTool();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        // 通貨ペア定義
        pairSettings();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        // 取引設定
        orderSettings();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
    }

    private void login() {

        driver.get("https://hirose-fx.co.jp/landing/tool_lp/#web");
        AutoTradeUtils.sleep(Duration.ofSeconds(1));

        String currentWindow = driver.getWindowHandle();
        driver.findElement(By.xpath("//a[@href='https://lionfx.hirose-fx.co.jp/WTChartWeb/index.html']")).click();

        driver.switchTo().window(driver.getWindowHandles().stream()
                .filter(window -> !window.equals(currentWindow))
                .findFirst()
                .orElse(currentWindow));

        while (true) {
            AutoTradeUtils.sleep(Duration.ofSeconds(5));
            try {
                driver.findElement(By.id("login-id")).sendKeys(username);
                driver.findElement(By.id("login-pw")).sendKeys(password);
                break;
            } catch (ElementNotInteractableException e) {
                log.warn(e.getMessage());
            }
        }
        driver.findElement(By.id("doLogin")).click();
    }

    private void cancelMessage() {
        try {
            driver.findElement(By.xpath("//button[starts-with(@id, 'message-cancel')]")).click();
        } catch (Exception e) {
            // 何もしない
        }
    }

    private void startUpTradeTool() {
        driver.manage().window().maximize();
    }

    private void orderSettings() {
        driver.findElement(By.id("fifo-on-label-quick")).click();
        driver.findElement(By.id("all-confm-quick")).click();
        driver.findElement(By.id("account-status")).click();
    }

    private void pairSettings() {
        // 通貨ペア設定
        // ヒロセではここで設定した通貨ペアに変更可能
        driver.findElement(By.id("brand-menu-button")).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        driver.findElement(By.id("brand-regist-nocheck")).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        List<WebElement> elements = driver.findElements(By.xpath("//li[@class='brand-regist-list-item']"));
        pairManager.getDescriptions().stream().forEach(pair -> {
            elements.stream()
                    .filter(e -> !e.findElements(By.xpath(MessageFormat.format("div[contains(text(),\"{0}\")]", pair)))
                            .isEmpty())
                    .findFirst()
                    .get()
                    .click();
        });
        driver.findElement(By.id("brand-regist-ok")).click();
    }

    @Override
    public String getPair() {
        return driver.findElement(By.id("order-brand")).getText();
    }

    @Override
    public String getMargin() {
        return driver.findElement(By.xpath("//div[@id='account-status-01-value']")).getText();
    }

    @Override
    public String getEffectiveMargin() {
        return driver.findElement(By.xpath("//div[@id='account-status-02-value']")).getText();
    }

    @Override
    public String getAskLot() {
        return driver.findElement(By.xpath("//div[@id='order-quick']/div[3]/div[2]/div/div[3]")).getText()
                .replace("　(0)", "");
    }

    @Override
    public String getBidLot() {
        return driver.findElement(By.xpath("//div[@id='order-quick']/div[3]/div[2]/div/div[1]")).getText()
                .replace("　(0)", "");
    }

    @Override
    public String getAskAverageRate() {
        return driver.findElement(By.xpath("//div[@class='buy-avg-rate total-row-base']")).getText();
    }

    @Override
    public String getBidAverageRate() {
        return driver.findElement(By.xpath("//div[@class='sell-avg-rate total-row-base']")).getText();
    }

    @Override
    public String getAskPipProfit() {
        return driver.findElement(By.xpath("//div[@class='buy-pip-profit total-row-base']")).getText();
    }

    @Override
    public String getBidPipProfit() {
        return driver.findElement(By.xpath("//div[@class='sell-pip-profit total-row-base']")).getText();
    }

    @Override
    public String getBidRate() {
        return driver.findElement(By.xpath("//span[@class='bid1']")).getText()
                + driver.findElement(By.xpath("//span[@class='bid2']")).getText()
                + driver.findElement(By.xpath("//span[@class='bid3']")).getText();
    }

    @Override
    public String getBidRateFromList(Pair pair) {
        String xpath = MessageFormat.format("//th[@id=''tab_rate_bid_{0}'']//span[starts-with(@class, ''bid'')]",
                pair.getCode());
        List<WebElement> elements = driver.findElements(By.xpath(xpath));
        return elements.stream().map(WebElement::getText).collect(Collectors.joining());
    }

    @Override
    public String getAskRate() {
        return driver.findElement(By.xpath("//span[@class='ask1']")).getText()
                + driver.findElement(By.xpath("//span[@class='ask2']")).getText()
                + driver.findElement(By.xpath("//span[@class='ask3']")).getText();
    }

    @Override
    public String getAskRateFromList(Pair pair) {
        String xpath = MessageFormat.format("//th[@id=''tab_rate_ask_{0}'']//span[starts-with(@class, ''ask'')]",
                pair.getCode());
        List<WebElement> elements = driver.findElements(By.xpath(xpath));
        return elements.stream().map(WebElement::getText).collect(Collectors.joining());
    }

    @Override
    public String getRateDiffFromList(Pair pair) {
        String xpath = MessageFormat.format("//span[@class=''rate_diff_{0}'']", pair.getCode());
        return driver.findElement(By.xpath(xpath)).getText();
    }

    @Override
    public void setLot(int lot) {
        String lastLot = driver.findElement(By.id("lot-param-quick-val")).getAttribute("value");
        if (!lastLot.equals(String.valueOf(lot))) {
            driver.findElement(By.id("lot-param-quick-val")).sendKeys(Keys.chord(Keys.CONTROL, "a"));
            driver.findElement(By.id("lot-param-quick-val")).sendKeys(String.valueOf(lot));
        }
    }

    @Override
    public void orderAsk() {
        try {
            driver.findElement(By.id("buy-panel-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void orderBid() {
        try {
            driver.findElement(By.id("sell-panel-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void fixAll() {
        try {
            driver.findElement(By.id("brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void fixAsk() {
        try {
            driver.findElement(By.id("buy-brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void fixBid() {
        try {
            driver.findElement(By.id("sell-brand-all-close-quick")).click();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void displayRateList() {
        driver.findElement(By.xpath("//li[@aria-controls='rate-menu']")).click();
    }

    @Override
    public void displayChart() {
        driver.findElement(By.xpath("//li[@aria-controls='chart-menu']")).click();
    }

    @Override
    public void changePair(String pair) {
        driver.findElement(By.xpath(
                MessageFormat.format("//tbody[@id=''rate-table'']//div[contains(text(),\"{0}\")]", pair))).click();
        AutoTradeUtils.sleep(Duration.ofSeconds(1));
        orderSettings();
    }

    @Override
    public void quit() {
        driver.quit();
    }

}
