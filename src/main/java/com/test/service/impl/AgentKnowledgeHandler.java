package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.entity.FinancialKnowledge;
import com.test.service.AIService;
import com.test.service.FinancialKnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentKnowledgeHandler {

    @Autowired
    private FinancialKnowledgeService financialKnowledgeService;

    @Autowired
    private AIService aiService;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        List<FinancialKnowledge> knowledgeList =
                financialKnowledgeService.semanticSearchKnowledge(message, 3);

        if (knowledgeList == null || knowledgeList.isEmpty()) {
            response.setReply("我暂时没有检索到相关财务知识，不过你可以换个说法，例如：怎么控制奶茶消费？");
            response.setActions(actions);
            return response;
        }

        StringBuilder knowledgeText = new StringBuilder();

        for (int i = 0; i < knowledgeList.size(); i++) {
            FinancialKnowledge knowledge = knowledgeList.get(i);

            knowledgeText.append(i + 1)
                    .append(". ")
                    .append(knowledge.getTitle());

            if (knowledge.getScore() != null) {
                knowledgeText.append("（相似度：")
                        .append(String.format("%.4f", knowledge.getScore()))
                        .append("）");
            }

            knowledgeText.append("：")
                    .append(knowledge.getContent())
                    .append("\n");
        }

        String prompt = "你是一个 AI 个人财务管家，请基于下面检索到的财务知识回答用户问题。\n"
                + "要求：\n"
                + "1. 回答要自然，不要机械复制知识库原文。\n"
                + "2. 建议要具体、可执行。\n"
                + "3. 不要编造知识库中没有依据的专业结论。\n"
                + "4. 可以结合用户的问题做适当解释。\n\n"
                + "【检索到的知识】\n"
                + knowledgeText
                + "\n【用户问题】\n"
                + message;

        String reply = aiService.chat("chat_" + sessionId, prompt);

        AgentAction action = new AgentAction();
        action.setName("retrieveKnowledge");
        action.setSuccess(true);
        action.setMessage("知识库检索成功，共检索到 " + knowledgeList.size() + " 条知识");

        actions.add(action);

        response.setReply(reply);
        response.setActions(actions);

        return response;
    }
}