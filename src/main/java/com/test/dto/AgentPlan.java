package com.test.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AgentPlan {

    private String intent;

    private String recordType;

    private BigDecimal amount;

    private String category;

    private String description;

    private Boolean valid;

    private String reason;

    private List<AgentExpenseItem> expenses;
}