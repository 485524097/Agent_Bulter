package com.test.task;

import com.test.dto.BudgetWarningResult;
import com.test.entity.Budget;
import com.test.service.BudgetService;
import com.test.service.BudgetWarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.YearMonth;
import java.util.List;

@Component
public class BudgetScheduleTask {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetWarningService budgetWarningService;

    /**
     * 每天晚上 22:00 执行预算巡检
     */
    @Scheduled(cron = "0 0 22 * * ?")
//    @Scheduled(cron = "0/30 * * * * ?")
    public void dailyBudgetCheck() {

        String budgetMonth = YearMonth.now().toString();

        System.out.println("开始执行每日预算巡检，budgetMonth=" + budgetMonth);

        List<Budget> budgets = budgetService.listEnabledBudgets(budgetMonth);

        if (budgets == null || budgets.isEmpty()) {
            System.out.println("当前月份暂无启用预算，budgetMonth=" + budgetMonth);
            return;
        }

        for (Budget budget : budgets) {
            if (budget == null || budget.getUserId() == null) {
                continue;
            }

            List<BudgetWarningResult> warningResults =
                    budgetWarningService.checkAfterExpense(
                            budget.getUserId(),
                            budget.getCategory()
                    );

            if (warningResults == null || warningResults.isEmpty()) {
                continue;
            }

            for (BudgetWarningResult result : warningResults) {
                if (result == null || !Boolean.TRUE.equals(result.getWarning())) {
                    continue;
                }

                System.out.println("定时预算巡检触发预警：userId="
                        + budget.getUserId()
                        + "，category="
                        + result.getCategory()
                        + "，level="
                        + result.getLevel()
                        + "，message="
                        + result.getMessage());
            }
        }

        System.out.println("每日预算巡检完成，budgetMonth=" + budgetMonth);
    }
}