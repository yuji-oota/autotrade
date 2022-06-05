package autotrade.local;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import autotrade.local.autotrader.AbstractAutoTrader;
import lombok.extern.slf4j.Slf4j;

@Profile("!test")
@Component
@Slf4j
public class ApplicationRunnerImpl implements ApplicationRunner {

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${autotrade.implementation}")
    private String beanName;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        AbstractAutoTrader autoTrader = applicationContext.getBean(beanName, AbstractAutoTrader.class);
        try {
            autoTrader.preOperation();
            while (true) {
                autoTrader.operation();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

}
