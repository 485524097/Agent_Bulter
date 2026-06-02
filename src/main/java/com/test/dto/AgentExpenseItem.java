package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AgentExpenseItem {

    private BigDecimal amount;

    private String category;

    private String recordType;

    private String description;
}