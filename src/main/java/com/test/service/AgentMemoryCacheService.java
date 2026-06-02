package com.test.service;

import java.util.List;

public interface AgentMemoryCacheService {

    void appendMessage(String sessionId, String role, String content);

    List<String> getRecentMessages(String sessionId);

    void deleteMemory(String sessionId);
}