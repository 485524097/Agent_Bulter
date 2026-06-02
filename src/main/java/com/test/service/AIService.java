package com.test.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

public interface AIService {

    /**
     * 1. 解析用户意图，返回 JSON
     */
    @SystemMessage(fromResource = "agent-plan-prompt.txt")
    String plan(@MemoryId String sessionId, @UserMessage String message);

    /**
     * 2. 普通闲聊，一次性返回完整回复
     */
    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(@MemoryId String sessionId, @UserMessage String message);

    /**
     * 3. 查询 / 统计结果润色
     */
    @SystemMessage(fromResource = "agent-reply-prompt.txt")
    String generateReply(@MemoryId String sessionId, @UserMessage String context);

    /**
     * 4. 普通闲聊，流式返回
     */
    @SystemMessage(fromResource = "system-prompt.txt")
    TokenStream streamChat(@MemoryId String sessionId, @UserMessage String message);
}