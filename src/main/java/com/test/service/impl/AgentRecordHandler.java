package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentExpenseItem;
import com.test.dto.AgentPlan;
import com.test.dto.BudgetWarningResult;
import com.test.mq.event.ExpenseRecordCreatedEvent;
import com.test.mq.producer.ExpenseRecordEventProducer;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentRecordHandler {

    @Autowired
    private BudgetWarningService budgetWarningService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    @Autowired
    private ExpenseRecordEventProducer expenseRecordEventProducer;

    public AgentChatResponse handle(Long userId,
                                    String sessionId,
                                    String message,
                                    AgentPlan plan,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        // 1. 判断 AI 是否解析成功
        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的记账信息，请换一种说法，例如：今天奶茶18。");
            response.setActions(actions);
            return response;
        }

        // 2. 构建待保存的记录列表
        // 优先使用 plan.expenses；如果没有，则兼容旧的单笔字段
        List<AgentExpenseItem> recordItems = buildRecordItems(plan);

        if (recordItems == null || recordItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 3. 循环保存多笔记录
        List<Long> recordIds = new ArrayList<>();
        List<AgentExpenseItem> savedItems = new ArrayList<>();

        for (AgentExpenseItem item : recordItems) {

            if (item == null) {
                continue;
            }

            if (item.getAmount() == null || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String recordType = normalizeRecordType(item.getRecordType());
            String category = normalizeCategory(item.getCategory());
            String description = normalizeDescription(item.getDescription());

            Long recordId = expenseRecordService.createRecord(
                    userId,
                    item.getAmount(),
                    category,
                    recordType,
                    description,
                    LocalDate.now(),
                    "AGENT",
                    message,
                    sessionId
            );


            ExpenseRecordCreatedEvent event = new ExpenseRecordCreatedEvent();
            event.setRecordId(recordId);
            event.setUserId(userId);
            event.setSessionId(sessionId);
            event.setRecordType(recordType);
            event.setCategory(category);
            event.setAmount(item.getAmount());
            event.setDescription(description);
            event.setSourceText(message);
            event.setEventTime(LocalDateTime.now());

            expenseRecordEventProducer.sendRecordCreatedEvent(event);

            recordIds.add(recordId);

            AgentExpenseItem savedItem = new AgentExpenseItem();
            savedItem.setAmount(item.getAmount());
            savedItem.setCategory(category);
            savedItem.setRecordType(recordType);
            savedItem.setDescription(description);

            savedItems.add(savedItem);
        }

        if (savedItems.isEmpty()) {
            response.setReply("我识别到你想记账，但没有识别到有效金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        // 4. 添加记账 action
        AgentAction recordAction = new AgentAction();

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            if ("INCOME".equalsIgnoreCase(item.getRecordType())) {
                recordAction.setName("recordIncome");
                recordAction.setMessage("收入记录已保存，记录ID：" + recordIds.get(0));
            } else {
                recordAction.setName("recordExpense");
                recordAction.setMessage("支出记录已保存，记录ID：" + recordIds.get(0));
            }
        } else {
            recordAction.setName("recordBatch");
            recordAction.setMessage("多笔记账成功，共保存" + savedItems.size() + "条记录，记录ID：" + recordIds);
        }

        recordAction.setSuccess(true);
        actions.add(recordAction);

        // 5. 生成基础回复
        String reply = buildReply(savedItems);

        // 6. 保存完所有记录后，再统一检查预算预警
//        List<BudgetWarningResult> warningResults = checkBudgetWarnings(userId, savedItems);
//
//        if (warningResults != null && !warningResults.isEmpty()) {
//            for (BudgetWarningResult warningResult : warningResults) {
//                reply = reply + warningResult.getMessage();
//
//                AgentAction warningAction = new AgentAction();
//                warningAction.setName("budgetWarning");
//                warningAction.setSuccess(true);
//                warningAction.setMessage("触发预算预警，分类："
//                        + warningResult.getCategory()
//                        + "，风险等级："
//                        + warningResult.getLevel());
//
//                actions.add(warningAction);
//            }
//        }

        // 7. 一定要设置 reply
        response.setReply(reply);
        response.setActions(actions);

        return response;
    }

    /**
     * 优先使用 AI 返回的 expenses。
     * 如果 expenses 为空，则兼容旧版单笔字段。
     */
    private List<AgentExpenseItem> buildRecordItems(AgentPlan plan) {

        List<AgentExpenseItem> items = plan.getExpenses();

        if (items != null && !items.isEmpty()) {
            return items;
        }

        List<AgentExpenseItem> fallbackItems = new ArrayList<>();

        AgentExpenseItem item = new AgentExpenseItem();
        item.setAmount(plan.getAmount());
        item.setCategory(plan.getCategory());
        item.setRecordType(plan.getRecordType());
        item.setDescription(plan.getDescription());

        fallbackItems.add(item);

        return fallbackItems;
    }

    /**
     * 多笔记账后统一检查预算预警。
     * 这里会检查：
     * 1. 总预算 TOTAL
     * 2. 本次涉及到的分类预算
     *
     * 用 Map 去重，避免多笔同类支出重复提示。
     */
    private List<BudgetWarningResult> checkBudgetWarnings(Long userId,
                                                          List<AgentExpenseItem> savedItems) {

        Map<String, BudgetWarningResult> warningMap = new LinkedHashMap<>();

        for (AgentExpenseItem item : savedItems) {

            if (!"EXPENSE".equalsIgnoreCase(item.getRecordType())) {
                continue;
            }

            List<BudgetWarningResult> results = budgetWarningService.checkAfterExpense(
                    userId,
                    item.getCategory()
            );

            if (results == null || results.isEmpty()) {
                continue;
            }

            for (BudgetWarningResult result : results) {
                if (result == null || !Boolean.TRUE.equals(result.getWarning())) {
                    continue;
                }

                String key = result.getCategory() + "_" + result.getLevel();
                warningMap.putIfAbsent(key, result);
            }
        }

        return new ArrayList<>(warningMap.values());
    }

    /**
     * 生成回复文案
     */
    private String buildReply(List<AgentExpenseItem> savedItems) {

        if (savedItems.size() == 1) {
            AgentExpenseItem item = savedItems.get(0);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            return "好嘞，已帮你记下一笔"
                    + typeText
                    + "："
                    + item.getCategory()
                    + item.getAmount()
                    + "元，"
                    + item.getDescription()
                    + "。";
        }

        StringBuilder reply = new StringBuilder();

        reply.append("好嘞，已帮你记下")
                .append(savedItems.size())
                .append("笔记录：");

        for (int i = 0; i < savedItems.size(); i++) {
            AgentExpenseItem item = savedItems.get(i);

            String typeText = "INCOME".equalsIgnoreCase(item.getRecordType()) ? "收入" : "支出";

            reply.append(typeText)
                    .append(item.getCategory())
                    .append(item.getAmount())
                    .append("元");

            if (i < savedItems.size() - 1) {
                reply.append("、");
            } else {
                reply.append("。");
            }
        }

        return reply.toString();
    }

    private String normalizeRecordType(String recordType) {
        if ("INCOME".equalsIgnoreCase(recordType)) {
            return "INCOME";
        }
        return "EXPENSE";
    }

    private String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "其他";
        }
        return category.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return "日常记录";
        }
        return description.trim();
    }
}