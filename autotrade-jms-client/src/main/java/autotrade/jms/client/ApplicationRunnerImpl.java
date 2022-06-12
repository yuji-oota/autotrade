package autotrade.jms.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import autotrade.common.material.JmsMessage;
import lombok.extern.slf4j.Slf4j;

@Profile("!test")
@Component
@Slf4j
public class ApplicationRunnerImpl implements ApplicationRunner {

    @Autowired
    private JmsTemplate jmsTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.getOptionNames().contains("key")) {
            log.info("application terminated. because key option is not exist.");
            System.exit(0);
        }
        String key = args.getOptionValues("key").get(0);

        String value = null;
        if (args.getOptionNames().contains("value")) {
            value = args.getOptionValues("value").get(0);
        }

        JmsMessage sendMessage = new JmsMessage(key, value);
        if ("keys".equals(key)) {
            sendMessage.setValue("jms-client");
            log.info("send massage:{}", sendMessage);
            jmsTemplate.convertAndSend("autotrade-local-keys", sendMessage);
            log.info("recieve massage:{}", jmsTemplate.receiveAndConvert(sendMessage.getValue()));
        } else {
            log.info("send massage:{}", sendMessage);
            jmsTemplate.convertAndSend("autotrade-local", sendMessage);
        }

        System.exit(0);
    }

}
