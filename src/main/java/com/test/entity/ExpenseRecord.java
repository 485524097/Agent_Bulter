package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("expense_record")
public class ExpenseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BigDecimal amount;

    private Long categoryId;

    private String category;

    /**
     * EXPENSE 支出，INCOME 收入
     */
    private String recordType;

    private String description;

    /**
     * 记账日期：例如 2026-05-29
     */
    private LocalDate expenseTime;

    /**
     * 具体发生时间，可选
     */
    private LocalDateTime expenseDatetime;

    private Long accountId;

    /**
     * AGENT、MANUAL、IMPORT
     */
    private String sourceType;

    private String sourceText;

    private String sessionId;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}