package com.test.service;

import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


public interface AgentService {

    AgentChatResponse chat(Long userId,String sessionId,String message);

}
