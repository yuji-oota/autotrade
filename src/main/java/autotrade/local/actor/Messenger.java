package autotrade.local.actor;

import autotrade.local.utility.AutoTradeProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

public class Messenger {

    private static final String KEY_REDIS_URI = "aws.elasticache.redis.uri";
    private static final String KEY_REDIS_CHANNEL = "aws.elasticache.redis.channel";

    public static StatefulRedisPubSubConnection<String, String> createPubSubConnection(RedisPubSubListener<String, String> listener) {
        RedisClient redisClient = RedisClient.create(AutoTradeProperties.get(KEY_REDIS_URI));
        StatefulRedisPubSubConnection<String, String> pubSubConnection = redisClient.connectPubSub();
        pubSubConnection.addListener(listener);
        pubSubConnection.sync().subscribe(AutoTradeProperties.get(KEY_REDIS_CHANNEL));
        return pubSubConnection;
    }

    public static void set(String key, String value) {
        RedisClient redisClient = RedisClient.create(AutoTradeProperties.get(KEY_REDIS_URI));
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            connection.sync().set(key, value);
        } finally {
            redisClient.shutdown();
        }
    }
    public static String get(String key) {
        RedisClient redisClient = RedisClient.create(AutoTradeProperties.get(KEY_REDIS_URI));
        try (StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            return connection.sync().get(key);
        } finally {
            redisClient.shutdown();
        }
    }

}
