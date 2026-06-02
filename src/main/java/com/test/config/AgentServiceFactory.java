package com.test.config;

import com.test.service.AIService;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentServiceFactory {

    @Bean
    public AIService aiService(ChatModel chatModel,
                               StreamingChatModel streamingChatModel) {

        return AiServices.builder(AIService.class)

                // 普通一次性回复模型
                .chatModel(chatModel)

                // 流式回复模型
                .streamingChatModel(streamingChatModel)

                // 会话记忆
                .chatMemoryProvider(memoryId ->
                        MessageWindowChatMemory.withMaxMessages(10)
                )

                .build();
    }
}