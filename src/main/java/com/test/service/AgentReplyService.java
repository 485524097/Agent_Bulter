package com.test.service;

import java.math.BigDecimal;

public interface AgentReplyService {

    String generateQueryReply(String sessionId,
                              String userMessage,
                              String timeText,
                              BigDecimal totalExpense,
                              BigDecimal totalIncome,
                              BigDecimal balance);
}