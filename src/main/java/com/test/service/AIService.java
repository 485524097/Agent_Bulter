package com.test.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AIService {

    @SystemMessage(fromResource = "agent-plan-prompt.txt")
    String plan(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(@MemoryId String sessionId, @UserMessage String message);

    @SystemMessage(fromResource = "agent-reply-prompt.txt")
    String generateReply(@MemoryId String sessionId, @UserMessage String context);
}