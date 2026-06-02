package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget")
public class Budget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 预算月份，例如 2026-05
     */
    private String budgetMonth;

    private Long categoryId;

    /**
     * TOTAL 表示总预算，餐饮/饮品/交通表示分类预算
     */
    private String category;

    private BigDecimal amount;

    /**
     * 预警比例，例如 80 表示使用 80% 时预警
     */
    private BigDecimal warningRate;

    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}