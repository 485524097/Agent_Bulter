package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.Budget;

import java.math.BigDecimal;
import java.util.List;

public interface BudgetService extends IService<Budget> {

    Long setBudget(Long userId,
                   String budgetMonth,
                   String category,
                   BigDecimal amount);

    Budget getBudget(Long userId,
                     String budgetMonth,
                     String category);
    List<Budget> listEnabledBudgets(String budgetMonth);
}