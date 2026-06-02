package com.test.service.impl;

import com.test.service.AgentMemoryCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class AgentMemoryCacheServiceImpl implements AgentMemoryCacheService {

    private static final String KEY_PREFIX = "agent:chat:memory:";

    /**
     * 每个 sessionId 最多保留最近 10 条消息
     */
    private static final int MAX_MESSAGE_COUNT = 10;

    /**
     * Redis 会话缓存 24 小时过期
     */
    private static final Duration TTL = Duration.ofHours(24);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void appendMessage(String sessionId, String role, String content) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        String key = buildKey(sessionId);

        String value = role + "：" + content + "｜" + LocalDateTime.now();

        // 1. 从右侧追加消息，保证时间顺序
        stringRedisTemplate.opsForList().rightPush(key, value);

        // 2. 只保留最近 10 条
        stringRedisTemplate.opsForList().trim(key, -MAX_MESSAGE_COUNT, -1);

        // 3. 设置过期时间
        stringRedisTemplate.expire(key, TTL);
    }

    @Override
    public List<String> getRecentMessages(String sessionId) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String key = buildKey(sessionId);

        List<String> messages = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (messages == null) {
            return Collections.emptyList();
        }

        return messages;
    }

    private String buildKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    @Override
    public void deleteMemory(String sessionId) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        String key = buildKey(sessionId);

        stringRedisTemplate.delete(key);
    }
}