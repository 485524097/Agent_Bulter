package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatSessionDTO;
import com.test.dto.AgentChatVO;
import com.test.service.AgentChatMessageService;
import com.test.service.AgentChatSessionService;
import com.test.service.AgentMemoryCacheService;
import com.test.service.AgentService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class BulterAgentController {

    @Autowired
    private AgentService agentService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {
//        使用token拦截器获取userid
        Long userId = UserContext.getUserId();

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }
    @GetMapping("/history/{sessionId}")
    public ResultVO<List<AgentChatMessageDTO>> history(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        List<AgentChatMessageDTO> list = agentChatMessageService.listBySession(userId, sessionId);

        return ResultVOUtil.success(list);
    }

    @GetMapping("/sessions")
    public ResultVO<List<AgentChatSessionDTO>> sessions() {

        Long userId = UserContext.getUserId();

        List<AgentChatSessionDTO> list = agentChatSessionService.listUserSessions(userId);

        return ResultVOUtil.success(list);
    }
    @DeleteMapping("/session/{sessionId}")
    public ResultVO deleteSession(@PathVariable String sessionId) {

        Long userId = UserContext.getUserId();

        // 1. 删除会话表
        agentChatSessionService.deleteSession(userId, sessionId);

        // 2. 删除消息表
        agentChatMessageService.deleteBySession(userId, sessionId);

        // 3. 删除 Redis 短期记忆
        agentMemoryCacheService.deleteMemory(sessionId);

        return ResultVOUtil.success("会话删除成功");
    }
}