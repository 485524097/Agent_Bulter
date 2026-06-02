package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExpenseRecordDTO {

    private Long id;

    private BigDecimal amount;

    private String category;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    private String description;

    private LocalDate expenseTime;

    private LocalDateTime expenseDatetime;

    private String sourceType;

    private String sourceText;

    private String sessionId;
}