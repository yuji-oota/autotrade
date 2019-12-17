package autotrade.local.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageListener extends RedisPubSubAdapter<String, String> {

    enum ReservedMessage {
        NONE,
        UPLOADLOG,
        AUTOTRADELOG,
        FIXASK,
        FIXBID,
        FIXALL,
        FORCESAME,
        SNAPSHOT,
    }

    private Map<ReservedMessage, Runnable> commandMap;

    public MessageListener() {
        commandMap = new HashMap<>();
        commandMap.put(ReservedMessage.NONE, () -> log.info("message is not available."));
    }

    public MessageListener putCommand(ReservedMessage command, Runnable consumer) {
        commandMap.put(command, consumer);
        return this;
    }

    @Override
    public void message(String channel, String message) {
        log.info("message recieved. channel {} message {}", channel, message);
        ReservedMessage command = Stream.of(ReservedMessage.values())
                .filter(value -> value.name().equals(message.toUpperCase()))
                .findFirst()
                .orElse(ReservedMessage.NONE);
        if (commandMap.containsKey(command)) {
            commandMap.get(command).run();
        }
    }

}
