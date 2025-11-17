package org.redis.exceptions;

public class KeyConflictException extends IllegalStateException {
    public KeyConflictException(String existingKey, String keyType) {
        super("Redis key '%s' already exists with type '%s'".formatted(existingKey, keyType));
    }
}
