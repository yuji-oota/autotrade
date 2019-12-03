package autotrade.local.actor;

import autotrade.local.utility.AutoTradeProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

public class Messenger {

    private RedisClient redisClient;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;

    public Messenger(RedisPubSubListener<String, String> listener) {

        redisClient = RedisClient.create(AutoTradeProperties.get("aws.elasticache.redis.uri"));
        pubSubConnection = redisClient.connectPubSub();
        pubSubConnection.addListener(listener);
        String channel = AutoTradeProperties.get("aws.elasticache.redis.channel");
        pubSubConnection.sync().subscribe(channel);
        set("channel", channel);
    }

    public void set(String key, String value) {
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            connection.sync().set(key, value);
        }
    }

}
