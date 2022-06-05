package autotrade.local.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JmsMessageListener {

    private ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private Map<String, Consumer<String>> consumerMap = new HashMap<>();

    @JmsListener(destination = "autotrade-local")
    void handleMessage(String message) throws JsonMappingException, JsonProcessingException {
        log.info("recieve massage:{}", message);
        Entry<String, String> messageEntry = objectMapper.readValue(message, new TypeReference<>() {
        });

        if (!consumerMap.containsKey(messageEntry.getKey())) {
            log.info("message is not handled. because message key is not exist.");
            return;
        }
        consumerMap.get(messageEntry.getKey()).accept(messageEntry.getValue());
    }

    public void addHandle(String key, Consumer<String> consumer) {
        consumerMap.put(key, consumer);
    }

}
