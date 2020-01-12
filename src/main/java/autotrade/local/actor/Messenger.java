package autotrade.local.actor;

import java.time.Duration;

import autotrade.local.utility.AutoTradeProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Messenger {

    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisPubSubListener<String, String> listener;
    private long lastConnected;
    private String redisUri;

    public Messenger(RedisPubSubListener<String, String> listener) {
        this.listener = listener;
        redisUri = AutoTradeProperties.get("aws.elasticache.redis.uri");
        redisClient = RedisClient.create(redisUri);
        connectPubSub();
    }

    private void connectPubSub() {
        pubSubConnection = redisClient.connectPubSub();
        pubSubConnection.addListener(listener);
        String channel = AutoTradeProperties.get("aws.elasticache.redis.channel");
        pubSubConnection.sync().subscribe(channel);
        lastConnected = System.currentTimeMillis();
    }

    public void set(String key, String value) {
        RedisClient client = RedisClient.create(redisUri);
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            connection.sync().set(key, value);
        } finally {
            client.shutdown();
        }
    }
    public String get(String key) {
        RedisClient client = RedisClient.create(redisUri);
        try (StatefulRedisConnection<String, String> connection = client.connect()) {
            return connection.sync().get(key);
        } finally {
            client.shutdown();
        }
    }

    public void reConnectPubSub() {
        if (System.currentTimeMillis() - lastConnected > Duration.ofMinutes(60).toMillis()) {
            log.info("reConnect");
            if (pubSubConnection.isOpen()) {
                pubSubConnection.close();
            }
            connectPubSub();
        }
    }

}
