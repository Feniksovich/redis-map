package org.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ScanParams;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Реализация {@link java.util.Map}, которая хранит данные в Redis в виде одной hash-структуры.
 * <p>Каждому экземпляру {@code RedisMap} соответствует один ключ Redis (типа {@code hash}),
 * в котором:</p>
 * <ul>
 *     <li>field — сериализованный ключ {@code K},</li>
 *     <li>value — сериализованное значение {@code V}.</li>
 * </ul>
 * <p>Сериализация и десериализация выполняются с помощью {@link ObjectMapper}.</p>
 *
 * @param <K> тип ключей карты
 * @param <V> тип значений карты
 */
public class RedisMap<K, V> implements Map<K, V> {

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final String redisKey;
    private final Class<K> keyClass;
    private final Class<V> valueClass;

    private static final int SCAN_CHUNK_SIZE = 32;
    private static final ScanParams SCAN_PARAMS = new ScanParams().count(SCAN_CHUNK_SIZE);

    /**
     * Основной конструктор с полностью настраиваемыми зависимостями.
     */
    protected RedisMap(JedisPool jedisPool,
                       ObjectMapper objectMapper,
                       String redisKey,
                       Class<K> keyClass,
                       Class<V> valueClass) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.redisKey = Objects.requireNonNull(redisKey, "redisKey must not be null");
        this.keyClass = Objects.requireNonNull(keyClass, "keyClass must not be null");
        this.valueClass = Objects.requireNonNull(valueClass, "valueClass must not be null");
    }

    private String serializeKey(K key) {
        Objects.requireNonNull(key, "key must not be null");
        try {
            return objectMapper.writeValueAsString(key);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize key: " + key, ex);
        }
    }

    private String serializeValue(V value) {
        Objects.requireNonNull(value, "value must not be null");
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize value: " + value, ex);
        }
    }

    private K deserializeKey(String data) {
        try {
            return objectMapper.readValue(data, keyClass);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deserialize key from: " + data, ex);
        }
    }

    private V deserializeValue(String data) {
        try {
            return objectMapper.readValue(data, valueClass);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deserialize value from: " + data, ex);
        }
    }

    @Override
    public int size() {
        try (final Jedis jedis = jedisPool.getResource()) {
            return Math.toIntExact(jedis.hlen(redisKey));
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        Objects.requireNonNull(key, "key must not be null");

        if (!keyClass.isInstance(key)) {
            return false;
        }

        final String field = serializeKey(keyClass.cast(key));
        try (final Jedis jedis = jedisPool.getResource()) {
            return jedis.hexists(redisKey, field);
        }
    }

    @Override
    public boolean containsValue(Object value) {
        Objects.requireNonNull(value, "value must not be null");

        if (!valueClass.isInstance(value)) {
            return false;
        }

        final String serializedValue = serializeValue(valueClass.cast(value));
        try (final Jedis jedis = jedisPool.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                final var scanResult = jedis.hscan(redisKey, cursor, SCAN_PARAMS);
                for (final Map.Entry<String, String> entry : scanResult.getResult()) {
                    if (Objects.equals(entry.getValue(), serializedValue)) {
                        return true;
                    }
                }
                cursor = scanResult.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            return false;
        }
    }

    @Override
    public V get(Object key) {
        Objects.requireNonNull(key, "key must not be null");

        if (!keyClass.isInstance(key)) {
            return null;
        }

        final String field = serializeKey(keyClass.cast(key));
        try (final Jedis jedis = jedisPool.getResource()) {
            final String rawValue = jedis.hget(redisKey, field);
            return rawValue != null
                    ? deserializeValue(rawValue)
                    : null;
        }
    }

    @Override
    public V put(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");

        final String field = serializeKey(key);
        final String rawValue = serializeValue(value);

        try (final Jedis jedis = jedisPool.getResource()) {
            final String prevRawValue = jedis.hget(redisKey, field);
            jedis.hset(redisKey, field, rawValue);
            return prevRawValue != null
                    ? deserializeValue(prevRawValue)
                    : null;
        }
    }

    @Override
    public V remove(Object key) {
        Objects.requireNonNull(key, "key must not be null");

        if (!keyClass.isInstance(key)) {
            return null;
        }

        final String field = serializeKey(keyClass.cast(key));
        try (final Jedis jedis = jedisPool.getResource()) {
            final String rawValue = jedis.hget(redisKey, field);

            if (rawValue == null) {
                return null;
            }

            jedis.hdel(redisKey, field);
            return deserializeValue(rawValue);
        }
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        Objects.requireNonNull(m, "m must not be null");

        if (m.isEmpty()) {
            return;
        }

        final Map<String, String> toPush = new HashMap<>(m.size());
        for (final Entry<? extends K, ? extends V> e : m.entrySet()) {
            Objects.requireNonNull(e.getKey(), "key must not be null");
            Objects.requireNonNull(e.getValue(), "value must not be null");
            final String field = serializeKey(e.getKey());
            final String rawValue = serializeValue(e.getValue());
            toPush.put(field, rawValue);
        }

        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.hset(redisKey, toPush);
        }
    }

    @Override
    public void clear() {
        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.del(redisKey);
        }
    }

    @Override
    public Set<K> keySet() {
        try (final Jedis jedis = jedisPool.getResource()) {
            return jedis.hkeys(redisKey)
                    .stream()
                    .map(this::deserializeKey)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Override
    public Collection<V> values() {
        try (final Jedis jedis = jedisPool.getResource()) {
            return jedis.hvals(redisKey)
                    .stream()
                    .map(this::deserializeValue)
                    .toList();
        }
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        try (final Jedis jedis = jedisPool.getResource()) {
            final Map<String, String> raw = jedis.hgetAll(redisKey);
            final Set<Entry<K, V>> result = new HashSet<>(raw.size());
            for (final Map.Entry<String, String> e : raw.entrySet()) {
                final K key = deserializeKey(e.getKey());
                final V value = deserializeValue(e.getValue());
                result.add(new AbstractMap.SimpleEntry<>(key, value));
            }
            return Collections.unmodifiableSet(result);
        }
    }

    @Override
    public String toString() {
        return "RedisMap{" +
                "redisKey='" + redisKey + '\'' +
                ", size=" + size() +
                '}';
    }
}
