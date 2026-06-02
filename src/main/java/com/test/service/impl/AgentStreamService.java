package com.test.service.impl;

import com.test.service.AIService;
import com.test.service.AgentChatMessageService;
import com.test.service.AgentChatSessionService;
import com.test.service.AgentMemoryCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Service
public class AgentStreamService {

    @Autowired
    private AIService aiService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    public SseEmitter streamChat(Long userId,
                                 String sessionId,
                                 String message) {

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + UUID.randomUUID().toString().replace("-", "");
        }

        String finalSessionId = sessionId;

        SseEmitter emitter = new SseEmitter(60_000L);

        StringBuilder fullReply = new StringBuilder();

        try {
            // 1. 先把 sessionId 返回给前端
            emitter.send(SseEmitter.event()
                    .name("session")
                    .data(finalSessionId));

            // 2. 保存用户消息到 MySQL
            agentChatMessageService.saveMessage(
                    userId,
                    finalSessionId,
                    "USER",
                    message,
                    null,
                    "CHAT_STREAM"
            );

            // 3. 保存用户消息到 Redis
            agentMemoryCacheService.appendMessage(
                    finalSessionId,
                    "USER",
                    message
            );

            // 4. 调用流式 AI
            aiService.streamChat(finalSessionId, message)
                    .onPartialResponse(partialResponse -> {
                        try {
                            fullReply.append(partialResponse);

                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(partialResponse));

                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onCompleteResponse(chatResponse -> {
                        try {
                            String reply = fullReply.toString();

                            // 5. 保存助手回复到 MySQL
                            agentChatMessageService.saveMessage(
                                    userId,
                                    finalSessionId,
                                    "ASSISTANT",
                                    reply,
                                    "CHAT",
                                    "CHAT_STREAM"
                            );

                            // 6. 保存助手回复到 Redis
                            agentMemoryCacheService.appendMessage(
                                    finalSessionId,
                                    "ASSISTANT",
                                    reply
                            );

                            // 7. 更新会话列表
                            agentChatSessionService.createOrUpdateSession(
                                    userId,
                                    finalSessionId,
                                    message,
                                    reply
                            );

                            emitter.send(SseEmitter.event()
                                    .name("done")
                                    .data("[DONE]"));

                            emitter.complete();

                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .onError(throwable -> {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("流式回复失败：" + throwable.getMessage()));
                        } catch (IOException ignored) {
                        }

                        emitter.complete();
                    })
                    .start();

        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("流式接口异常：" + e.getMessage()));
            } catch (IOException ignored) {
            }

            emitter.complete();
        }

        return emitter;
    }
}