package com.test.mq.event;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExpenseRecordCreatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long recordId;

    private Long userId;

    private String sessionId;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    private String category;

    private BigDecimal amount;

    private String description;

    private String sourceText;

    private LocalDateTime eventTime;
}