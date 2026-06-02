package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.Budget;
import com.test.mapper.BudgetMapper;
import com.test.service.BudgetService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BudgetServiceImpl extends ServiceImpl<BudgetMapper, Budget> implements BudgetService {

    @Override
    public Long setBudget(Long userId,
                          String budgetMonth,
                          String category,
                          BigDecimal amount) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (budgetMonth == null || budgetMonth.trim().isEmpty()) {
            throw new RuntimeException("预算月份不能为空");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("预算金额必须大于0");
        }

        if (category == null || category.trim().isEmpty()) {
            category = "TOTAL";
        }

        QueryWrapper<Budget> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId)
                .eq("budget_month", budgetMonth)
                .eq("category", category)
                .eq("deleted", 0);

        Budget budget = this.getOne(queryWrapper);

        if (budget == null) {
            budget = new Budget();
            budget.setUserId(userId);
            budget.setBudgetMonth(budgetMonth);
            budget.setCategory(category);
            budget.setAmount(amount);
            budget.setWarningRate(new BigDecimal("80.00"));
            budget.setEnabled(1);
            budget.setDeleted(0);

            this.save(budget);
        } else {
            budget.setAmount(amount);
            budget.setEnabled(1);

            this.updateById(budget);
        }

        return budget.getId();
    }

    @Override
    public Budget getBudget(Long userId,
                            String budgetMonth,
                            String category) {

        if (userId == null) {
            throw new RuntimeException("用户ID不能为空");
        }

        if (budgetMonth == null || budgetMonth.trim().isEmpty()) {
            throw new RuntimeException("预算月份不能为空");
        }

        if (category == null || category.trim().isEmpty()) {
            category = "TOTAL";
        }

        QueryWrapper<Budget> queryWrapper = new QueryWrapper<>();

        queryWrapper.eq("user_id", userId)
                .eq("budget_month", budgetMonth)
                .eq("category", category)
                .eq("enabled", 1)
                .eq("deleted", 0);

        return this.getOne(queryWrapper);
    }
}