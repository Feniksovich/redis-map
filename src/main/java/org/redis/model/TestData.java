package org.redis.model;

import java.util.List;

public record TestData(String id, int count, boolean active, List<String> tags) {}
