package com.test.service;

public interface AgentRateLimitService {

    boolean tryAcquire(Long userId, String scene, int limit, long ttlSeconds);
}