package com.enterprise.booking.rag;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RedisSessionMemoryService implements SessionMemoryService {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionMemoryService.class);
    private static final String KEY_PREFIX = "session:turns:";
    private final StringRedisTemplate redisTemplate;

    public RedisSessionMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void appendTurn(String sessionId, String role, String content) {
        log.info("appendTurn start sessionId={} role={} contentLength={}",
                sessionId, role, content == null ? 0 : content.length());
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(key, role + ": " + content);
        redisTemplate.expire(key, java.time.Duration.ofDays(7));
        log.info("appendTurn done key={}", key);
    }

    @Override
    public List<String> latestTurns(String sessionId, int limit) {
        log.info("latestTurns start sessionId={} limit={}", sessionId, limit);
        String key = KEY_PREFIX + sessionId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            log.info("latestTurns empty sessionId={}", sessionId);
            return List.of();
        }
        long start = Math.max(0, size - limit);
        List<String> values = redisTemplate.opsForList().range(key, start, size - 1);
        List<String> turns = values == null ? List.of() : new ArrayList<>(values);
        log.info("latestTurns done sessionId={} returned={}", sessionId, turns.size());
        return turns;
    }
}
