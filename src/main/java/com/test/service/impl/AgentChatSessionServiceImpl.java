package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.dto.AgentChatSessionDTO;
import com.test.entity.AgentChatSession;
import com.test.mapper.AgentChatSessionMapper;
import com.test.service.AgentChatSessionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentChatSessionServiceImpl
        extends ServiceImpl<AgentChatSessionMapper, AgentChatSession>
        implements AgentChatSessionService {

    @Override
    public void createOrUpdateSession(Long userId,
                                      String sessionId,
                                      String userMessage,
                                      String assistantReply) {

        if (userId == null || sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }

        String lastMessage = assistantReply;
        if (lastMessage == null || lastMessage.trim().isEmpty()) {
            lastMessage = userMessage;
        }

        QueryWrapper<AgentChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("session_id", sessionId)
                .eq("deleted", 0);

        AgentChatSession session = this.getOne(queryWrapper);

        if (session == null) {
            session = new AgentChatSession();

            session.setUserId(userId);
            session.setSessionId(sessionId);
            session.setTitle(buildTitle(userMessage));
            session.setLastMessage(trimText(lastMessage, 100));
            session.setLastActiveTime(LocalDateTime.now());
            session.setDeleted(0);

            this.save(session);
        } else {
            session.setLastMessage(trimText(lastMessage, 100));
            session.setLastActiveTime(LocalDateTime.now());

            this.updateById(session);
        }
    }

    @Override
    public List<AgentChatSessionDTO> listUserSessions(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        QueryWrapper<AgentChatSession> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("deleted", 0)
                .orderByDesc("last_active_time");

        List<AgentChatSession> sessions = this.list(queryWrapper);

        List<AgentChatSessionDTO> result = new ArrayList<>();

        for (AgentChatSession session : sessions) {
            AgentChatSessionDTO dto = new AgentChatSessionDTO();

            dto.setId(session.getId());
            dto.setSessionId(session.getSessionId());
            dto.setTitle(session.getTitle());
            dto.setLastMessage(session.getLastMessage());
            dto.setLastActiveTime(session.getLastActiveTime());

            result.add(dto);
        }

        return result;
    }

    private String buildTitle(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "新会话";
        }
        return trimText(message, 20);
    }

    private String trimText(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
    @Override
    public void deleteSession(Long userId, String sessionId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new RuntimeException("会话ID不能为空");
        }

        QueryWrapper<AgentChatSession> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("session_id", sessionId)
                .eq("deleted", 0);

        this.remove(queryWrapper);
    }
}