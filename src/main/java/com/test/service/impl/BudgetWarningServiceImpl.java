package com.test.service.impl;

import com.test.dto.BudgetWarningResult;
import com.test.entity.Budget;
import com.test.entity.BudgetWarningLog;
import com.test.mapper.BudgetWarningLogMapper;
import com.test.service.BudgetService;
import com.test.service.BudgetWarningService;
import com.test.service.ExpenseRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
public class BudgetWarningServiceImpl implements BudgetWarningService {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private ExpenseRecordService expenseRecordService;

    @Autowired
    private BudgetWarningLogMapper budgetWarningLogMapper;


    @Override
    public BudgetWarningResult checkMonthlyTotalBudget(Long userId) {

        BudgetWarningResult result = new BudgetWarningResult();

        if (userId == null) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setMessage("");
            return result;
        }

        YearMonth yearMonth = YearMonth.now();
        String budgetMonth = yearMonth.toString();

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        Budget budget = budgetService.getBudget(
                userId,
                budgetMonth,
                "TOTAL"
        );

        if (budget == null || budget.getAmount() == null) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setMessage("");
            return result;
        }

        BigDecimal budgetAmount = budget.getAmount();

        if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setMessage("");
            return result;
        }

        BigDecimal usedAmount = expenseRecordService.sumAmountByDateRange(
                userId,
                "EXPENSE",
                startDate,
                endDate
        );

        BigDecimal remainingAmount = budgetAmount.subtract(usedAmount);

        BigDecimal usageRate = usedAmount
                .multiply(new BigDecimal("100"))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        BigDecimal warningRate = budget.getWarningRate();
        if (warningRate == null) {
            warningRate = new BigDecimal("80.00");
        }

        String level = "NORMAL";
        boolean warning = false;

        if (usageRate.compareTo(new BigDecimal("100")) >= 0) {
            level = "OVER";
            warning = true;
        } else if (usageRate.compareTo(new BigDecimal("90")) >= 0) {
            level = "DANGER";
            warning = true;
        } else if (usageRate.compareTo(warningRate) >= 0) {
            level = "WARNING";
            warning = true;
        }

        String message = "";

        if ("OVER".equals(level)) {
            message = "另外提醒一下，你本月预算已经超支，当前已支出 "
                    + usedAmount + " 元，预算为 "
                    + budgetAmount + " 元。";
        } else if ("DANGER".equals(level)) {
            message = "另外提醒一下，你本月预算使用率已经达到 "
                    + usageRate + "%，接下来要谨慎消费。";
        } else if ("WARNING".equals(level)) {
            message = "另外提醒一下，你本月预算使用率已经达到 "
                    + usageRate + "%，建议适当控制消费。";
        }

        result.setWarning(warning);
        result.setLevel(level);
        result.setMessage(message);
        result.setBudgetAmount(budgetAmount);
        result.setUsedAmount(usedAmount);
        result.setRemainingAmount(remainingAmount);
        result.setUsageRate(usageRate);

        if (warning) {
            BudgetWarningLog log = new BudgetWarningLog();

            log.setUserId(userId);
            log.setBudgetMonth(budgetMonth);
            log.setCategory("TOTAL");
            log.setBudgetAmount(budgetAmount);
            log.setUsedAmount(usedAmount);
            log.setUsageRate(usageRate);
            log.setWarningLevel(level);
            log.setWarningMessage(message);
            log.setNotifyType("SYSTEM");
            log.setNotifyStatus(1);

            budgetWarningLogMapper.insert(log);
        }

        return result;
    }
    @Override
    public List<BudgetWarningResult> checkAfterExpense(Long userId, String category) {

        List<BudgetWarningResult> results = new ArrayList<>();

        // 1. 检查总预算
        BudgetWarningResult totalResult = checkBudgetByCategory(userId, "TOTAL");
        if (totalResult != null && Boolean.TRUE.equals(totalResult.getWarning())) {
            results.add(totalResult);
        }

        // 2. 检查分类预算
        if (category != null && !category.trim().isEmpty()) {
            BudgetWarningResult categoryResult = checkBudgetByCategory(userId, category);
            if (categoryResult != null && Boolean.TRUE.equals(categoryResult.getWarning())) {
                results.add(categoryResult);
            }
        }

        return results;
    }
    private BudgetWarningResult checkBudgetByCategory(Long userId, String category) {

        BudgetWarningResult result = new BudgetWarningResult();

        if (userId == null) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setMessage("");
            return result;
        }

        YearMonth yearMonth = YearMonth.now();
        String budgetMonth = yearMonth.toString();

        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        Budget budget = budgetService.getBudget(userId, budgetMonth, category);

        if (budget == null || budget.getAmount() == null) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setCategory(category);
            result.setMessage("");
            return result;
        }

        BigDecimal budgetAmount = budget.getAmount();

        if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            result.setWarning(false);
            result.setLevel("NORMAL");
            result.setCategory(category);
            result.setMessage("");
            return result;
        }

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

        BigDecimal remainingAmount = budgetAmount.subtract(usedAmount);

        BigDecimal usageRate = usedAmount
                .multiply(new BigDecimal("100"))
                .divide(budgetAmount, 2, RoundingMode.HALF_UP);

        BigDecimal warningRate = budget.getWarningRate();
        if (warningRate == null) {
            warningRate = new BigDecimal("80.00");
        }

        String level = "NORMAL";
        boolean warning = false;

        if (usageRate.compareTo(new BigDecimal("100")) >= 0) {
            level = "OVER";
            warning = true;
        } else if (usageRate.compareTo(new BigDecimal("90")) >= 0) {
            level = "DANGER";
            warning = true;
        } else if (usageRate.compareTo(warningRate) >= 0) {
            level = "WARNING";
            warning = true;
        }

        String budgetName = "TOTAL".equals(category) ? "总预算" : category + "预算";

        String message = "";

        if ("OVER".equals(level)) {
            message = "另外提醒一下，你本月" + budgetName + "已经超支，当前已支出 "
                    + usedAmount + " 元，预算为 "
                    + budgetAmount + " 元。";
        } else if ("DANGER".equals(level)) {
            message = "另外提醒一下，你本月" + budgetName + "使用率已经达到 "
                    + usageRate + "%，接下来要谨慎消费。";
        } else if ("WARNING".equals(level)) {
            message = "另外提醒一下，你本月" + budgetName + "使用率已经达到 "
                    + usageRate + "%，建议适当控制消费。";
        }

        result.setWarning(warning);
        result.setLevel(level);
        result.setCategory(category);
        result.setMessage(message);
        result.setBudgetAmount(budgetAmount);
        result.setUsedAmount(usedAmount);
        result.setRemainingAmount(remainingAmount);
        result.setUsageRate(usageRate);

        if (warning) {
            BudgetWarningLog log = new BudgetWarningLog();

            log.setUserId(userId);
            log.setBudgetMonth(budgetMonth);
            log.setCategory(category);
            log.setBudgetAmount(budgetAmount);
            log.setUsedAmount(usedAmount);
            log.setUsageRate(usageRate);
            log.setWarningLevel(level);
            log.setWarningMessage(message);
            log.setNotifyType("SYSTEM");
            log.setNotifyStatus(1);

            budgetWarningLogMapper.insert(log);
        }

        return result;
    }
}