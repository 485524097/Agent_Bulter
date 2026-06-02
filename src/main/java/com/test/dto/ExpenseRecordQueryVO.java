package com.test.dto;

import lombok.Data;

@Data
public class ExpenseRecordQueryVO {

    /**
     * today / month / all
     */
    private String range;

    /**
     * EXPENSE / INCOME
     */
    private String recordType;

    /**
     * 餐饮、饮品、交通等
     */
    private String category;

    /**
     * 当前页
     */
    private Long pageNo = 1L;

    /**
     * 每页大小
     */
    private Long pageSize = 10L;
}