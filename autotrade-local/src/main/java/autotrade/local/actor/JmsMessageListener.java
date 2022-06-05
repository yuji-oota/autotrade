package autotrade.local.actor;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JmsMessageListener {

    @JmsListener(destination = "autotrade-local")
    void handleMessage(String message) {
        log.info("recieve massage:{}", message);
    }
    
}
