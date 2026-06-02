package com.test.service;

import com.test.dto.BudgetWarningResult;

import java.util.List;

public interface BudgetWarningService {

    /**
     * 检查本月总预算是否触发预警
     */

    BudgetWarningResult checkMonthlyTotalBudget(Long userId);
    List<BudgetWarningResult> checkAfterExpense(Long userId, String category);
}