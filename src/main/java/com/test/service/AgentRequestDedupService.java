package com.test.service;

public interface AgentRequestDedupService {

    boolean isDuplicate(Long userId, String sessionId, String message);
}