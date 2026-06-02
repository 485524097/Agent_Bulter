package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentChatSessionDTO;
import com.test.entity.AgentChatSession;

import java.util.List;

public interface AgentChatSessionService extends IService<AgentChatSession> {

    void createOrUpdateSession(Long userId,
                               String sessionId,
                               String userMessage,
                               String assistantReply);

    List<AgentChatSessionDTO> listUserSessions(Long userId);

    void deleteSession(Long userId, String sessionId);
}