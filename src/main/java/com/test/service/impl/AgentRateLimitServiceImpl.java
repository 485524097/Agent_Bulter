package com.test.service.impl;

import com.test.service.AgentRateLimitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AgentRateLimitServiceImpl implements AgentRateLimitService {

    private static final String KEY_PREFIX = "rate:agent:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean tryAcquire(Long userId, String scene, int limit, long ttlSeconds) {

        if (userId == null) {
            return false;
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "default";
        }

        String key = KEY_PREFIX + scene + ":" + userId;

        Long count = stringRedisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
        }

        return count != null && count <= limit;
    }
}