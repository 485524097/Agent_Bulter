package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BudgetWarningResult {

    private Boolean warning;

    private String level;

    private String category;

    private String message;

    private BigDecimal budgetAmount;

    private BigDecimal usedAmount;

    private BigDecimal remainingAmount;

    private BigDecimal usageRate;
}