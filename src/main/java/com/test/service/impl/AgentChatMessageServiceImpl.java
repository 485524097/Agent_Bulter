package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.dto.AgentChatMessageDTO;
import com.test.entity.AgentChatMessage;
import com.test.entity.AgentChatSession;
import com.test.mapper.AgentChatMessageMapper;
import com.test.service.AgentChatMessageService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentChatMessageServiceImpl
        extends ServiceImpl<AgentChatMessageMapper, AgentChatMessage>
        implements AgentChatMessageService {

    @Override
    public void saveMessage(Long userId,
                            String sessionId,
                            String role,
                            String content,
                            String intent,
                            String finalIntent) {

        if (userId == null || sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            return;
        }

        AgentChatMessage message = new AgentChatMessage();

        message.setUserId(userId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setIntent(intent);
        message.setFinalIntent(finalIntent);
        message.setDeleted(0);

        this.save(message);
    }
    @Override
    public List<AgentChatMessageDTO> listBySession(Long userId, String sessionId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new RuntimeException("会话ID不能为空");
        }

        QueryWrapper<AgentChatMessage> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("session_id", sessionId)
                .eq("deleted", 0)
                .orderByAsc("create_time");

        List<AgentChatMessage> messageList = this.list(queryWrapper);

        List<AgentChatMessageDTO> result = new ArrayList<>();

        for (AgentChatMessage message : messageList) {
            AgentChatMessageDTO dto = new AgentChatMessageDTO();

            dto.setId(message.getId());
            dto.setRole(message.getRole());
            dto.setContent(message.getContent());
            dto.setIntent(message.getIntent());
            dto.setFinalIntent(message.getFinalIntent());
            dto.setCreateTime(message.getCreateTime());

            result.add(dto);
        }

        return result;
    }
    @Override
    public void deleteBySession(Long userId, String sessionId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new RuntimeException("会话ID不能为空");
        }

        QueryWrapper<AgentChatMessage> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("session_id", sessionId)
                .eq("deleted", 0);

        this.remove(queryWrapper);
    }
}