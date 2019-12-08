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

    public Messenger(RedisPubSubListener<String, String> listener) {
        this.listener = listener;
        redisClient = RedisClient.create(AutoTradeProperties.get("aws.elasticache.redis.uri"));
        connect();
    }

    private void connect() {
        pubSubConnection = redisClient.connectPubSub();
        pubSubConnection.addListener(listener);
        String channel = AutoTradeProperties.get("aws.elasticache.redis.channel");
        pubSubConnection.sync().subscribe(channel);
        lastConnected = System.currentTimeMillis();
    }

    public void set(String key, String value) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            connection.sync().set(key, value);
        } finally {
            pubSubConnection.close();
            connect();
        }
    }
    public String get(String key) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            return connection.sync().get(key);
        }
    }

    public void reConnect() {
        if (System.currentTimeMillis() - lastConnected > Duration.ofMinutes(60).toMillis()) {
            log.info("reConnect");
            pubSubConnection.close();
            connect();
        }
    }
}
