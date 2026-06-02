package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.entity.Budget;
import com.test.service.BudgetService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class AgentBudgetRiskHandler {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    public AgentChatResponse handle(Long userId,
                                    String message,
                                    List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        if (userId == null) {
            response.setReply("用户ID不能为空，无法判断预算风险。");
            response.setActions(actions);
            return response;
        }

        // 1. 当前先默认判断本月总预算
        YearMonth yearMonth = YearMonth.now();
        String budgetMonth = yearMonth.toString();

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 2. 查询本月总预算
        String category = resolveBudgetCategory(message);

        Budget budget = budgetService.getBudget(
                userId,
                budgetMonth,
                category
        );

        if (budget == null || budget.getAmount() == null) {
            response.setReply("你还没有设置" + budgetMonth + "的总预算，请先设置预算，例如：帮我把本月预算设置为1800。");
            response.setActions(actions);
            return response;
        }

        BigDecimal budgetAmount = budget.getAmount();

        if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            response.setReply("当前预算金额无效，请重新设置预算。");
            response.setActions(actions);
            return response;
        }

        // 3. 查询本月支出
        BigDecimal usedAmount;

        if ("TOTAL".equals(category)) {
            usedAmount = expenseRecordService.sumAmountByDateRange(
                    userId,
                    "EXPENSE",
                    startDate,
                    endDate
            );
        } else {
            usedAmount = expenseRecordService.sumAmountByDateRangeAndCategory(
                    userId,
                    "EXPENSE",
                    category,
                    startDate,
                    endDate
            );
        }

        // 4. 计算剩余预算
        BigDecimal remainingAmount = budgetAmount.subtract(usedAmount);

        // 5. 计算使用率
        BigDecimal usageRate = usedAmount
                .multiply(new BigDecimal("100"))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        // 6. 判断风险等级
        String level;
        String advice;

        if (usageRate.compareTo(new BigDecimal("100")) >= 0) {
            level = "OVER";
            advice = "已经超出预算，建议接下来控制非必要支出。";
        } else if (usageRate.compareTo(new BigDecimal("90")) >= 0) {
            level = "DANGER";
            advice = "预算使用率已经很高，接下来要谨慎消费。";
        } else if (usageRate.compareTo(new BigDecimal("80")) >= 0) {
            level = "WARNING";
            advice = "预算使用率接近预警线，建议适当控制消费。";
        } else {
            level = "NORMAL";
            advice = "目前预算比较安全，可以继续保持。";
        }

        // 7. 添加 action
        AgentAction action = new AgentAction();
        action.setName("checkBudgetRisk");
        action.setSuccess(true);
        action.setMessage("预算风险判断成功，风险等级：" + level);
        actions.add(action);

        // 8. 返回回复
        String budgetName = "TOTAL".equals(category) ? "总预算" : category + "预算";

        response.setReply("你" + budgetMonth + "的" + budgetName + "是 "
                + budgetAmount
                + " 元，目前已支出 "
                + usedAmount
                + " 元，预算使用率为 "
                + usageRate
                + "%，剩余预算 "
                + remainingAmount
                + " 元。"
                + advice);
        response.setActions(actions);

        return response;
    }
    private String resolveBudgetCategory(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "TOTAL";
        }

        if (message.contains("餐饮") || message.contains("吃饭") || message.contains("饭")) {
            return "餐饮";
        }

        if (message.contains("饮品") || message.contains("奶茶") || message.contains("咖啡")) {
            return "饮品";
        }

        if (message.contains("交通") || message.contains("打车") || message.contains("地铁") || message.contains("公交")) {
            return "交通";
        }

        if (message.contains("购物") || message.contains("买东西")) {
            return "购物";
        }

        if (message.contains("娱乐") || message.contains("电影") || message.contains("游戏")) {
            return "娱乐";
        }

        if (message.contains("学习") || message.contains("买书") || message.contains("课程")) {
            return "学习";
        }

        if (message.contains("住房") || message.contains("房租")) {
            return "住房";
        }

        if (message.contains("医疗") || message.contains("看病") || message.contains("药")) {
            return "医疗";
        }

        return "TOTAL";
    }
}