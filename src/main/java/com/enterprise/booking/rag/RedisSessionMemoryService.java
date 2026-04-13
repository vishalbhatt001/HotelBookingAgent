package com.enterprise.booking.rag;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RedisSessionMemoryService implements SessionMemoryService {

    private static final String KEY_PREFIX = "session:turns:";
    private final StringRedisTemplate redisTemplate;

    public RedisSessionMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void appendTurn(String sessionId, String role, String content) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(key, role + ": " + content);
        redisTemplate.expire(key, java.time.Duration.ofDays(7));
    }

    @Override
    public List<String> latestTurns(String sessionId, int limit) {
        String key = KEY_PREFIX + sessionId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return List.of();
        }
        long start = Math.max(0, size - limit);
        List<String> values = redisTemplate.opsForList().range(key, start, size - 1);
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
