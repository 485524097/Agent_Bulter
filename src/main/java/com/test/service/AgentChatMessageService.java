package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentChatMessageDTO;
import com.test.entity.AgentChatMessage;

import java.util.List;

public interface AgentChatMessageService extends IService<AgentChatMessage> {

    void saveMessage(Long userId,
                     String sessionId,
                     String role,
                     String content,
                     String intent,
                     String finalIntent);

    List<AgentChatMessageDTO> listBySession(Long userId, String sessionId);

    void deleteBySession(Long userId, String sessionId);
}