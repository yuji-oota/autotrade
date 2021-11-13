package autotrade.local.actor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.lettuce.core.pubsub.RedisPubSubAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MessageListener extends RedisPubSubAdapter<String, String> {

    public enum ReservedMessage {
        NONE,
        UPLOADLOG,
        AUTOTRADELOG,
        FIXASK,
        FIXBID,
        FIXALL,
        FORCESAME,
        SNAPSHOT,
        THROUGHORDER,
        THROUGHFIX,
        IGNORESPREAD,
        AUTORECOMMENDED,
        SAVECOUNTERTRADINGTHRESHOLD,
        CHANGEPAIR,
        CHANGERECOMMENDED,
        DISPLAYCHART,
        DISPLAYRATELIST,
        FORCEEXCEPTION,
        CHANGEABLEPAIRADD,
        CHANGEABLEPAIRREMOVE,
        LOADSAMESNAPSHOT,
        RESERVELIMITFIXASK,
        RESERVELIMITFIXBID,
        RESERVESTOPFIXASK,
        RESERVESTOPFIXBID,
        CLOUDSAVE,
        CLOUDLOAD,
        RESETSAME,
        CLOSERECOVERYMANAGER,
    }

    private Map<ReservedMessage, Consumer<String[]>> commandMap;

    public MessageListener() {
        commandMap = new HashMap<>();
        commandMap.put(ReservedMessage.NONE, (args) -> log.info("message is not available."));
    }

    public MessageListener putCommand(ReservedMessage command, Consumer<String[]> consumer) {
        commandMap.put(command, consumer);
        return this;
    }

    @Override
    public void message(String channel, String message) {
        log.info("message recieved. channel {} message {}", channel, message);
        String[] messageArray = message.split(" ");
        String key = messageArray[0];
        String[] args = Arrays.copyOfRange(messageArray, 1, messageArray.length);

        ReservedMessage command = Stream.of(ReservedMessage.values())
                .filter(value -> value.name().equals(key.toUpperCase()))
                .findFirst()
                .orElse(ReservedMessage.NONE);
        if (commandMap.containsKey(command)) {
            commandMap.get(command).accept(args);
        }
    }

}
