package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.Budget;

import java.math.BigDecimal;

public interface BudgetService extends IService<Budget> {

    Long setBudget(Long userId,
                   String budgetMonth,
                   String category,
                   BigDecimal amount);

    Budget getBudget(Long userId,
                     String budgetMonth,
                     String category);
}