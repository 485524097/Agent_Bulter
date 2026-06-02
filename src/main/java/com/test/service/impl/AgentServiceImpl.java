package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.enums.IntentType;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentServiceImpl implements AgentService {

    @Autowired
    private AgentPlanService agentPlanService;

    @Autowired
    private AgentIntentService agentIntentService;

    @Autowired
    private AgentRecordHandler agentRecordHandler;

    @Autowired
    private AgentStatisticsHandler agentStatisticsHandler;

    @Autowired
    private AgentBudgetHandler agentBudgetHandler;

    @Autowired
    private AgentBudgetRiskHandler agentBudgetRiskHandler;

    @Autowired
    private AgentChatReplyService agentChatReplyService;

    @Autowired
    private AgentChatMessageService agentChatMessageService;

    @Autowired
    private AgentActionLogService agentActionLogService;

    @Autowired
    private AgentMemoryCacheService agentMemoryCacheService;

    @Autowired
    private AgentChatSessionService agentChatSessionService;

    @Autowired
    private AgentRecordManageHandler agentRecordManageHandler;

    @Autowired
    private AgentKnowledgeHandler agentKnowledgeHandler;

    @Override
    public AgentChatResponse chat(Long userId, String sessionId, String message){

        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + java.util.UUID.randomUUID().toString().replace("-", "");
        }

        List<AgentAction> actions = new ArrayList<>();

        // 1. 保存用户消息到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "USER",
                message,
                null,
                null
        );

        // 2. 保存用户消息到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "USER",
                message
        );

        // 3. AI 解析计划
        AgentPlan plan = agentPlanService.parsePlan(sessionId, message);

        // 4. 后端最终意图裁决
        IntentType finalIntent = agentIntentService.decideIntent(message, plan);

        AgentChatResponse response;

        switch (finalIntent) {
            case LIST_RECORDS:
                response = agentRecordManageHandler.handleList(userId, message, actions);
                break;

            case DELETE_RECORD:
                response = agentRecordManageHandler.handleDelete(userId, message, actions);
                break;

            case UPDATE_RECORD:
                response = agentRecordManageHandler.handleUpdate(userId, message, actions);
                break;

            case UNDO_RECORD:
                response = agentRecordManageHandler.handleUndo(userId, message, actions);
                break;

            case RECORD_EXPENSE:
                response = agentRecordHandler.handle(userId, sessionId, message, plan, actions);
                break;

            case QUERY_EXPENSE:
                response = agentStatisticsHandler.handleQuery(userId, message, actions);
                break;

            case ANALYZE_EXPENSE:
                response = agentStatisticsHandler.handleAnalyze(userId, message, actions);
                break;

            case KNOWLEDGE_ADVICE:
                response = agentKnowledgeHandler.handle(userId, sessionId, message, actions);
                break;

            case SET_BUDGET:
                response = agentBudgetHandler.handleSetBudget(userId, message, plan, actions);
                break;

            case BUDGET_RISK:
                response = agentBudgetRiskHandler.handle(userId, message, actions);
                break;

            case CHAT:
            default:
                response = agentChatReplyService.chat(sessionId, message, actions);
                break;
        }

        // 5. 保存助手回复到 MySQL
        agentChatMessageService.saveMessage(
                userId,
                sessionId,
                "ASSISTANT",
                response.getReply(),
                plan == null ? null : plan.getIntent(),
                finalIntent == null ? null : finalIntent.name()
        );

        // 6. 保存助手回复到 Redis
        agentMemoryCacheService.appendMessage(
                sessionId,
                "ASSISTANT",
                response.getReply()
        );

        // 7. 保存 actions 到 MySQL
        agentActionLogService.saveActions(
                userId,
                sessionId,
                message,
                response.getActions()
        );
        // 8. 更新会话摘要
        agentChatSessionService.createOrUpdateSession(
                userId,
                sessionId,
                message,
                response.getReply()
        );

        // 返回前设置 sessionId
        response.setSessionId(sessionId);

        return response;
    }

}