package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.AgentChatMessageDTO;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentChatSessionDTO;
import com.test.dto.AgentChatVO;
import com.test.service.*;
import com.test.service.impl.AgentStreamService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @Autowired
    private AgentStreamService agentStreamService;

    @Autowired
    private AgentRateLimitService agentRateLimitService;

    @Autowired
    private AgentRequestDedupService agentRequestDedupService;

    @PostMapping("/chat")
    public ResultVO chat(@RequestBody AgentChatVO chatmessage) {

        Long userId = UserContext.getUserId();

        boolean allowed = agentRateLimitService.tryAcquire(
                userId,
                "chat",
                20,
                60
        );

        if (!allowed) {
            return ResultVOUtil.fail("请求太频繁，请稍后再试");
        }

        boolean duplicate = agentRequestDedupService.isDuplicate(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        if (duplicate) {
            return ResultVOUtil.fail("请勿重复提交相同请求");
        }

        AgentChatResponse chat = this.agentService.chat(
                userId,
                chatmessage.getSessionId(),
                chatmessage.getMessage()
        );

        return ResultVOUtil.success(chat);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AgentChatVO chatMessage) {

        Long userId = UserContext.getUserId();

        boolean allowed = agentRateLimitService.tryAcquire(
                userId,
                "chat",
                20,
                60
        );

        if (!allowed) {
            SseEmitter emitter = new SseEmitter();

            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("请求太频繁，请稍后再试"));
            } catch (Exception ignored) {
            }

            emitter.complete();

            return emitter;
        }

        boolean duplicate = agentRequestDedupService.isDuplicate(
                userId,
                chatMessage.getSessionId(),
                chatMessage.getMessage()
        );

        if (duplicate) {
            SseEmitter emitter = new SseEmitter();

            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("请勿重复提交相同请求"));
            } catch (Exception ignored) {
            }

            emitter.complete();

            return emitter;
        }

        return agentStreamService.streamChat(
                userId,
                chatMessage.getSessionId(),
                chatMessage.getMessage()
        );
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