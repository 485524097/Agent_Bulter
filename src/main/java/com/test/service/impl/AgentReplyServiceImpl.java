package com.test.service.impl;

import com.test.service.AIService;
import com.test.service.AgentReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AgentReplyServiceImpl implements AgentReplyService {

    @Autowired
    private AIService aiService;

    @Override
    public String generateQueryReply(String sessionId,
                                     String userMessage,
                                     String timeText,
                                     BigDecimal totalExpense,
                                     BigDecimal totalIncome,
                                     BigDecimal balance) {

        String context = "业务类型：收支查询\n"
                + "用户问题：" + userMessage + "\n"
                + "时间范围：" + timeText + "\n"
                + "支出金额：" + totalExpense + " 元\n"
                + "收入金额：" + totalIncome + " 元\n"
                + "结余金额：" + balance + " 元\n"
                + "请根据以上真实数据，生成一句自然、简洁的回复。";

        try {
            return aiService.generateReply(sessionId, context);
        } catch (Exception e) {
            // AI 润色失败时，回退固定模板，保证主流程不受影响
            if (userMessage != null && userMessage.contains("收入")) {
                return "你" + timeText + "收入共 " + totalIncome + " 元。";
            }

            if (userMessage != null && userMessage.contains("结余")) {
                return "你" + timeText + "收入共 " + totalIncome
                        + " 元，支出共 " + totalExpense
                        + " 元，结余 " + balance + " 元。";
            }

            return "你" + timeText + "支出共 " + totalExpense + " 元。";
        }
    }
}