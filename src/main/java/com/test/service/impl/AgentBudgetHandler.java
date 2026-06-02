package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.dto.AgentPlan;
import com.test.service.BudgetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
public class AgentBudgetHandler {

    @Autowired
    private BudgetService budgetService;

    public AgentChatResponse handleSetBudget(Long userId,
                                             String message,
                                             AgentPlan plan,
                                             List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法设置预算。");
            response.setActions(actions);
            return response;
        }

        if (plan == null || !Boolean.TRUE.equals(plan.getValid())) {
            response.setReply("我没有识别出完整的预算信息，请换一种说法，例如：帮我把本月预算设置为1800。");
            response.setActions(actions);
            return response;
        }

        if (plan.getAmount() == null) {
            response.setReply("我识别到你想设置预算，但没有识别到预算金额，请补充金额。");
            response.setActions(actions);
            return response;
        }

        String budgetMonth = YearMonth.now().toString();

        String category = plan.getCategory();
        if (category == null || category.trim().isEmpty()) {
            category = "TOTAL";
        }

        Long budgetId = budgetService.setBudget(
                userId,
                budgetMonth,
                category,
                plan.getAmount()
        );

        AgentAction action = new AgentAction();
        action.setName("setBudget");
        action.setSuccess(true);
        action.setMessage("预算设置成功，预算ID：" + budgetId);
        actions.add(action);

        if ("TOTAL".equals(category)) {
            response.setReply("已帮你把" + budgetMonth + "的总预算设置为 "
                    + plan.getAmount() + " 元。");
        } else {
            response.setReply("已帮你把" + budgetMonth + "的"
                    + category + "预算设置为 "
                    + plan.getAmount() + " 元。");
        }

        response.setActions(actions);

        return response;
    }
}