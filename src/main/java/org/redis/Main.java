package org.redis;

import org.redis.model.TestData;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        final Map<String, TestData> source = Map.of(
                "1", new TestData("1", 1, true, List.of("A", "B")),
                "2", new TestData("2", 2, false, List.of("C", "D")),
                "3", new TestData("3", 3, false, List.of("E", "F"))
        );

        final JedisPool jedisPool = new JedisPool(
                new JedisPoolConfig(),
                System.getenv("REDIS_HOST"),
                Integer.parseInt(System.getenv("REDIS_PORT"))
        );

        try (final RedisMapFactory factory = RedisMapFactory.builder()
                .jedisPool(jedisPool).build()
        ) {
            final Map<String, TestData> redisMap =
                    factory.create("test-data", String.class, TestData.class);

            redisMap.putAll(source);

            source.forEach((id, data) ->
                    LOGGER.info("%s: %s".formatted(id, data)));

            redisMap.forEach((id, data) ->
                    LOGGER.info("%s: %s".formatted(id, data)));
        }
    }
}