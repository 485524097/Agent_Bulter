package com.test.service.impl;

import com.test.service.AgentRequestDedupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

@Service
public class AgentRequestDedupServiceImpl implements AgentRequestDedupService {

    private static final String KEY_PREFIX = "dedup:agent:";

    /**
     * 5 秒内相同请求视为重复提交
     */
    private static final Duration TTL = Duration.ofSeconds(5);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean isDuplicate(Long userId, String sessionId, String message) {

        if (userId == null) {
            return false;
        }

        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "default";
        }

        String raw = userId + ":" + sessionId + ":" + message.trim();
        String hash = md5(raw);

        String key = KEY_PREFIX + hash;

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", TTL);

        // success = true，说明第一次提交，不重复
        // success = false，说明 key 已存在，是重复提交
        return !Boolean.TRUE.equals(success);
    }

    private String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();

            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}