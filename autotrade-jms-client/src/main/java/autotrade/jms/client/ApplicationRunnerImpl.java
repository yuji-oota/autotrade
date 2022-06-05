package autotrade.jms.client;

import java.util.AbstractMap.SimpleEntry;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Profile("!test")
@Component
@Slf4j
public class ApplicationRunnerImpl implements ApplicationRunner {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (args.getOptionNames().contains("key")) {
            log.info("application terminated. because key option is not exist.");
            System.exit(0);
        }
        
        String value = null;
        if (args.getOptionNames().contains("value")) {
            value = args.getOptionValues("value").get(0);
        }
        SimpleEntry<String, String> messageEntry = new SimpleEntry<>(args.getOptionValues("key").get(0), value);
        log.info("send massage:{}", messageEntry);
        jmsTemplate.convertAndSend("autotrade-local", messageEntry);
        System.exit(0);
    }

}
