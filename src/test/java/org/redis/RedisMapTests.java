package org.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.redis.model.TestData;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RedisMapTests {

    private RedisMap<String, TestData> redisMap;

    private static JedisPool jedisPool;
    private static ObjectMapper objectMapper;

    private static final String MAP_KEY = "test";
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    @BeforeAll
    static void setup() {
        jedisPool = new JedisPool(new JedisPoolConfig(), REDIS_HOST, REDIS_PORT);
        objectMapper = new ObjectMapper();
    }

    @AfterAll
    static void shutdown() {
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @BeforeEach
    void createMap() {
        redisMap = new RedisMap<>(jedisPool, objectMapper, MAP_KEY, String.class, TestData.class);
    }

    @AfterEach
    void destroyMap() {
        if (jedisPool != null) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(MAP_KEY);
            }
        }
    }

    @Test
    void size_shouldReturnZeroForEmptyMap() {
        final int size = redisMap.size();
        assertEquals(0, size);
    }

    @Test
    void size_shouldReturnCorrectSizeAfterAddingElements() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        redisMap.put("2", new TestData("2", 2, false, List.of("B")));

        final int size = redisMap.size();
        assertEquals(2, size);
    }

    @Test
    void isEmpty_shouldReturnTrueForEmptyMap() {
        final boolean isEmpty = redisMap.isEmpty();
        assertTrue(isEmpty);
    }

    @Test
    void isEmpty_shouldReturnFalseForNonEmptyMap() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));

        final boolean isEmpty = redisMap.isEmpty();
        assertFalse(isEmpty);
    }

    @Test
    void containsKey_shouldReturnTrueForExistingKey() {
        final String key = "1";
        redisMap.put(key, new TestData("1", 1, true, List.of("A")));

        final boolean contains = redisMap.containsKey(key);
        assertTrue(contains);
    }

    @Test
    void containsKey_shouldReturnFalseForNonExistingKey() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));

        final boolean contains = redisMap.containsKey("2");
        assertFalse(contains);
    }

    @Test
    @SuppressWarnings("SuspiciousMethodCalls")
    void containsKey_shouldReturnFalseForWrongTypeKey() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));

        final boolean contains = redisMap.containsKey(1);
        assertFalse(contains);
    }

    @Test
    void containsKey_shouldThrowExceptionForNullKey() {
        assertThrows(NullPointerException.class, () -> redisMap.containsKey(null));
    }

    @Test
    void containsValue_shouldReturnTrueForExistingValue() {
        final TestData value = new TestData("1", 1, true, List.of("A"));
        redisMap.put("1", value);

        final boolean contains = redisMap.containsValue(value);
        assertTrue(contains);
    }

    @Test
    void containsValue_shouldReturnFalseForNonExistingValue() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));

        final boolean contains = redisMap.containsValue(new TestData("2", 2, false, List.of("B")));
        assertFalse(contains);
    }

    @Test
    @SuppressWarnings("SuspiciousMethodCalls")
    void containsValue_shouldReturnFalseForWrongTypeValue() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        final boolean contains = redisMap.containsValue("wrong type");
        assertFalse(contains);
    }

    @Test
    void containsValue_shouldThrowExceptionForNullValue() {
        assertThrows(NullPointerException.class, () -> redisMap.containsValue(null));
    }

    @Test
    void get_shouldReturnValueForExistingKey() {
        final String key = "1";
        final TestData expectedValue = new TestData("1", 1, true, List.of("A"));
        redisMap.put(key, expectedValue);
        final TestData actualValue = redisMap.get(key);
        assertEquals(expectedValue, actualValue);
    }

    @Test
    void get_shouldReturnNullForNonExistingKey() {
        final TestData value = redisMap.get("non-existent");
        assertNull(value);
    }

    @Test
    @SuppressWarnings("SuspiciousMethodCalls")
    void get_shouldReturnNullForWrongTypeKey() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        final TestData value = redisMap.get(123);
        assertNull(value);
    }

    @Test
    void get_shouldThrowExceptionForNullKey() {
        assertThrows(NullPointerException.class, () -> redisMap.get(null));
    }

    @Test
    void put_shouldAddNewElementAndReturnNull() {
        final String key = "1";
        final TestData value = new TestData("1", 1, true, List.of("A"));
        final TestData previousValue = redisMap.put(key, value);
        assertNull(previousValue);
        assertEquals(value, redisMap.get(key));
        assertEquals(1, redisMap.size());
    }

    @Test
    void put_shouldReplaceExistingElementAndReturnOldValue() {
        final String key = "1";
        final TestData oldValue = new TestData("1", 1, true, List.of("A"));
        final TestData newValue = new TestData("1", 2, false, List.of("B"));
        redisMap.put(key, oldValue);
        final TestData previousValue = redisMap.put(key, newValue);
        assertEquals(oldValue, previousValue);
        assertEquals(newValue, redisMap.get(key));
        assertEquals(1, redisMap.size());
    }

    @Test
    void put_shouldThrowExceptionForNullKey() {
        final TestData value = new TestData("1", 1, true, List.of("A"));
        assertThrows(NullPointerException.class, () -> redisMap.put(null, value));
    }

    @Test
    void put_shouldThrowExceptionForNullValue() {
        assertThrows(NullPointerException.class, () -> redisMap.put("1", null));
    }

    @Test
    void remove_shouldRemoveElementAndReturnValue() {
        final String key = "1";
        final TestData value = new TestData("1", 1, true, List.of("A"));
        redisMap.put(key, value);
        final TestData removedValue = redisMap.remove(key);
        assertEquals(value, removedValue);
        assertNull(redisMap.get(key));
        assertEquals(0, redisMap.size());
    }

    @Test
    void remove_shouldReturnNullForNonExistingKey() {
        final TestData removedValue = redisMap.remove("non-existent");
        assertNull(removedValue);
    }

    @Test
    @SuppressWarnings("SuspiciousMethodCalls")
    void remove_shouldReturnNullForWrongTypeKey() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        final TestData removedValue = redisMap.remove(123);
        assertNull(removedValue);
    }

    @Test
    void remove_shouldThrowExceptionForNullKey() {
        assertThrows(NullPointerException.class, () -> redisMap.remove(null));
    }

    @Test
    void putAll_shouldAddAllElementsFromOtherMap() {
        final Map<String, TestData> source = Map.of(
                "1", new TestData("1", 1, true, List.of("A")),
                "2", new TestData("2", 2, false, List.of("B")),
                "3", new TestData("3", 3, false, List.of("C"))
        );
        redisMap.putAll(source);
        assertEquals(3, redisMap.size());
        assertEquals(source.get("1"), redisMap.get("1"));
        assertEquals(source.get("2"), redisMap.get("2"));
        assertEquals(source.get("3"), redisMap.get("3"));
    }

    @Test
    void putAll_shouldReplaceExistingElements() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        final Map<String, TestData> source = Map.of(
                "1", new TestData("1", 2, false, List.of("B")),
                "2", new TestData("2", 2, false, List.of("C"))
        );
        redisMap.putAll(source);
        assertEquals(2, redisMap.size());
        assertEquals(source.get("1"), redisMap.get("1"));
        assertEquals(source.get("2"), redisMap.get("2"));
    }

    @Test
    void putAll_shouldDoNothingForEmptyMap() {
        final Map<String, TestData> emptyMap = Map.of();
        redisMap.putAll(emptyMap);
        assertEquals(0, redisMap.size());
    }

    @Test
    void putAll_shouldThrowExceptionForNullMap() {
        assertThrows(NullPointerException.class, () -> redisMap.putAll(null));
    }

    @Test
    void putAll_shouldThrowExceptionForMapWithNullKey() {
        final Map<String, TestData> mapWithNullKey = new HashMap<>();
        mapWithNullKey.put(null, new TestData("1", 1, true, List.of("A")));
        assertThrows(NullPointerException.class, () -> redisMap.putAll(mapWithNullKey));
    }

    @Test
    void putAll_shouldThrowExceptionForMapWithNullValue() {
        final Map<String, TestData> mapWithNullValue = new HashMap<>();
        mapWithNullValue.put("1", null);
        assertThrows(NullPointerException.class, () -> redisMap.putAll(mapWithNullValue));
    }

    @Test
    void clear_shouldRemoveAllElements() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        redisMap.put("2", new TestData("2", 2, false, List.of("B")));
        assertEquals(2, redisMap.size());
        redisMap.clear();
        assertEquals(0, redisMap.size());
        assertTrue(redisMap.isEmpty());
    }

    @Test
    void clear_shouldNotThrowExceptionForEmptyMap() {
        assertDoesNotThrow(() -> redisMap.clear());
        assertEquals(0, redisMap.size());
    }

    @Test
    void keySet_shouldReturnSetOfAllKeys() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        redisMap.put("2", new TestData("2", 2, false, List.of("B")));
        redisMap.put("3", new TestData("3", 3, false, List.of("C")));
        final Set<String> keys = redisMap.keySet();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("1"));
        assertTrue(keys.contains("2"));
        assertTrue(keys.contains("3"));
    }

    @Test
    void keySet_shouldReturnUnmodifiableSet() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        final Set<String> keys = redisMap.keySet();
        assertThrows(UnsupportedOperationException.class, () -> keys.add("2"));
    }

    @Test
    void keySet_shouldReturnEmptySetForEmptyMap() {
        final Set<String> keys = redisMap.keySet();
        assertTrue(keys.isEmpty());
    }

    @Test
    void values_shouldReturnCollectionOfAllValues() {
        final TestData value1 = new TestData("1", 1, true, List.of("A"));
        final TestData value2 = new TestData("2", 2, false, List.of("B"));
        final TestData value3 = new TestData("3", 3, false, List.of("C"));
        redisMap.put("1", value1);
        redisMap.put("2", value2);
        redisMap.put("3", value3);
        final Collection<TestData> values = redisMap.values();
        assertEquals(3, values.size());
        assertTrue(values.contains(value1));
        assertTrue(values.contains(value2));
        assertTrue(values.contains(value3));
    }

    @Test
    void values_shouldReturnEmptyCollectionForEmptyMap() {
        final Collection<TestData> values = redisMap.values();
        assertTrue(values.isEmpty());
    }

    @Test
    void entrySet_shouldReturnSetOfAllEntries() {
        final TestData value1 = new TestData("1", 1, true, List.of("A"));
        final TestData value2 = new TestData("2", 2, false, List.of("B"));
        redisMap.put("1", value1);
        redisMap.put("2", value2);
        final Set<Map.Entry<String, TestData>> entries = redisMap.entrySet();
        assertEquals(2, entries.size());
        assertTrue(entries.stream()
                .anyMatch(e -> e.getKey().equals("1") && e.getValue().equals(value1)));
        assertTrue(entries.stream()
                .anyMatch(e -> e.getKey().equals("2") && e.getValue().equals(value2)));
    }

    @Test
    void entrySet_shouldReturnUnmodifiableSet() {
        redisMap.put("1", new TestData("1", 1, true, List.of("A")));
        final Set<Map.Entry<String, TestData>> entries = redisMap.entrySet();
        assertThrows(UnsupportedOperationException.class, () -> entries.add(
                new AbstractMap.SimpleEntry<>("2", new TestData("2", 2, false, List.of("B")))
        ));
    }

    @Test
    void entrySet_shouldReturnEmptySetForEmptyMap() {
        final Set<Map.Entry<String, TestData>> entries = redisMap.entrySet();
        assertTrue(entries.isEmpty());
    }
}
