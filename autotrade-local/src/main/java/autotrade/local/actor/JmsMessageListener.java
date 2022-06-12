package autotrade.local.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import autotrade.common.material.JmsMessage;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JmsMessageListener {

    @Autowired
    private JmsTemplate jmsTemplate;

    private Map<String, Consumer<String>> handlerMap = new HashMap<>();

    @JmsListener(destination = "autotrade-local-keys")
    public void keys(JmsMessage message) throws JsonMappingException, JsonProcessingException {
        jmsTemplate.convertAndSend(message.getValue(),
                new JmsMessage("keys", handlerMap.keySet().toString()));
    }

    @JmsListener(destination = "autotrade-local")
    public void handleMessage(JmsMessage message) {
        log.info("recieve massage:{}", message);
        if (!handlerMap.containsKey(message.getKey())) {
            log.info("message is not handled. because message key is not exist.");
            return;
        }
        handlerMap.get(message.getKey()).accept(message.getValue());
    }

    public void addHandler(String key, Consumer<String> consumer) {
        handlerMap.put(key, consumer);
    }

}
