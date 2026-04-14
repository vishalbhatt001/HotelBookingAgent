package com.enterprise.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AgentTraceService {

    private static final String TRACE_PREFIX = "trace:session:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AgentTraceService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void addTrace(String sessionId, Map<String, Object> trace) {
        try {
            String key = TRACE_PREFIX + sessionId;
            redisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(trace));
            redisTemplate.expire(key, Duration.ofDays(7));
        } catch (JsonProcessingException ignored) {
            // Ignore trace serialization errors.
        }
    }

    public List<String> traces(String sessionId, int limit) {
        String key = TRACE_PREFIX + sessionId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return List.of();
        }
        long start = Math.max(0, size - limit);
        List<String> values = redisTemplate.opsForList().range(key, start, size - 1);
        return values == null ? List.of() : new ArrayList<>(values);
    }
}
