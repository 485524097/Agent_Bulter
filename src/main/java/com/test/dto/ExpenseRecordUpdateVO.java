package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseRecordUpdateVO {

    private BigDecimal amount;

    private String category;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    private String description;

    private LocalDate expenseTime;
}