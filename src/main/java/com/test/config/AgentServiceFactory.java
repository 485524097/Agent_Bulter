package com.test.config;


import com.test.service.AIService;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentServiceFactory {

    @Autowired
    private ChatModel chatModel;

//
    @Autowired
    private ContentRetriever contentRetriever;
//
    @Autowired
    private StreamingChatModel streamingChatModel;

    @Bean
    public AIService aiService(ChatModel chatModel) {
        // 创建对话记忆窗口，最多保留最近 10 条消息，用于支持多轮对话上下文
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // 构建 AICodeService 的 AI 服务代理对象
        return AiServices.builder(AIService.class)

                // 注入大语言模型，负责生成回答
                .chatModel(chatModel)
//
                // 注入对话记忆，使 AI 能够理解上下文
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
//
//                // 注入 RAG 检索器，用于从知识库中检索相关内容辅助回答
//                .contentRetriever(contentRetriever)
//
//                // 流式输出
//                .streamingChatModel(streamingChatModel)

                // 构建服务对象，并注册到 Spring 容器中
                .build();

    }
}