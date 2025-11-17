package org.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redis.exceptions.KeyConflictException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Objects;

/**
 * Простая фабрика для создания экземпляров {@link RedisMap} с переиспользуемыми
 * {@link JedisPool} и {@link ObjectMapper}.
 */
public class RedisMapFactory implements AutoCloseable {

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    /**
     * Определяет стратегию проверки конфликтов Redis-ключей при создании карт.
     *
     * <p>Если значение {@code true}, то перед созданием каждой {@link RedisMap}
     * выполняется проверка того, что указанный Redis-ключ ещё не существует.
     * При обнаружении уже существующего ключа будет выброшено {@link KeyConflictException}.
     * Если значение {@code false}, проверка не выполняется, и фабрика может переиспользовать
     * существующие ключи.</p>
     */
    private final boolean checkKeyConflicts;

    private RedisMapFactory(JedisPool jedisPool, ObjectMapper objectMapper, boolean checkKeyConflicts) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool must not be null");
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.checkKeyConflicts = checkKeyConflicts;
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Возвращает текущее значение флага проверки конфликтов Redis-ключей.
     *
     * <p>Если метод возвращает {@code true}, фабрика при создании каждой
     * новой карты проверяет, что указанный Redis-ключ ещё не занят, и при
     * конфликте выбрасывает {@link KeyConflictException}. Если возвращается
     * {@code false}, такая проверка не выполняется и допускается использование
     * уже существующих ключей.</p>
     *
     * @return {@code true}, если включена проверка конфликтов ключей,
     *         {@code false} в противном случае
     */
    public boolean isCheckKeyConflicts() {
        return checkKeyConflicts;
    }

    /**
     * Создать новую карту, хранящую данные в Redis hash по заданному ключу.
     */
    public <K, V> RedisMap<K, V> create(String redisKey, Class<K> keyClass, Class<V> valueClass) {
        if (checkKeyConflicts) {
            ensureUnoccupiedRedisKey(jedisPool, redisKey);
        }
        return new RedisMap<>(jedisPool, objectMapper, redisKey, keyClass, valueClass);
    }

    /**
     * Создать типизированную фабрику, фиксирующую типы ключей и значений.
     * После этого при создании карт не нужно указывать классы.
     */
    public <K, V> TypedRedisMapFactory<K, V> typed(Class<K> keyClass, Class<V> valueClass) {
        return new TypedRedisMapFactory<>(this, keyClass, valueClass);
    }

    @Override
    public void close() {
        jedisPool.close();
    }

    /**
     * Типизированная фабрика для нескольких карт с одинаковыми K и V.
     */
    public static final class TypedRedisMapFactory<K, V> {

        private final RedisMapFactory redisMapFactory;
        private final Class<K> keyClass;
        private final Class<V> valueClass;

        private TypedRedisMapFactory(RedisMapFactory redisMapFactory,
                                     Class<K> keyClass,
                                     Class<V> valueClass) {
            this.redisMapFactory = redisMapFactory;
            this.keyClass = Objects.requireNonNull(keyClass, "keyClass must not be null");
            this.valueClass = Objects.requireNonNull(valueClass, "valueClass must not be null");
        }

        public RedisMap<K, V> create(String redisKey) {
            return redisMapFactory.create(redisKey, keyClass, valueClass);
        }
    }

    /**
     * Builder для конфигурирования и создания {@link RedisMapFactory}.
     */
    public static final class Builder {

        private JedisPool jedisPool;
        private ObjectMapper objectMapper;
        private boolean checkKeyConflicts = true;

        /**
         * Задает {@link JedisPool}, который будет использоваться
         * всеми экземплярами {@link RedisMap}, создаваемыми этой фабрикой.
         *
         * @param jedisPool пул соединений с Redis; не может быть {@code null}
         * @return текущий экземпляр билдера для цепочки вызовов
         */
        public Builder jedisPool(JedisPool jedisPool) {
            this.jedisPool = jedisPool;
            return this;
        }

        /**
         * Задает {@link ObjectMapper}, который будет использоваться
         * для сериализации и десериализации ключей и значений.
         *
         * <p>По умолчанию используется новый экземпляр {@link ObjectMapper}.</p>
         *
         * @param objectMapper пользовательский {@link ObjectMapper}
         * @return текущий экземпляр билдера для цепочки вызовов
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * Включает или отключает проверку конфликтов Redis-ключей при создании карт.
         *
         * <p>При значении {@code true} перед созданием новой карты проверяется,
         * что указанный Redis-ключ ещё не существует. Если ключ уже существует
         * (независимо от его типа), будет выброшено {@link KeyConflictException}.
         * По умолчанию проверка включена.</p>
         *
         * @param checkKeyConflicts {@code true}, чтобы запрещать создание карт на уже занятых ключах,
         *                          {@code false}, чтобы разрешить использование существующих ключей
         * @return текущий экземпляр билдера для цепочки вызовов
         */
        public Builder checkKeyConflicts(boolean checkKeyConflicts) {
            this.checkKeyConflicts = checkKeyConflicts;
            return this;
        }

        /**
         * Создаёт новый экземпляр {@link RedisMapFactory} с настроенными параметрами.
         *
         * <p>Параметр {@code jedisPool} является обязательным и должен быть
         * задан через {@link #jedisPool(JedisPool)}. Если {@code objectMapper}
         * не был задан, фабрика использует новый {@link ObjectMapper} по умолчанию.</p>
         *
         * @return сконфигурированная фабрика {@link RedisMapFactory}
         * @throws java.lang.NullPointerException если {@code jedisPool} не задан
         */
        public RedisMapFactory build() {
            return new RedisMapFactory(jedisPool, objectMapper, checkKeyConflicts);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Проверяет, что Redis-ключ не существует.
     *
     * <p>Если ключ уже существует в Redis (независимо от его типа),
     * будет выброшено {@link KeyConflictException}.</p>
     */
    private static void ensureUnoccupiedRedisKey(JedisPool jedisPool, String redisKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            final String type = jedis.type(redisKey);
            if (!"none".equalsIgnoreCase(type)) {
                throw new KeyConflictException(redisKey, type);
            }
        }
    }
}
