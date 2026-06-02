package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("budget_warning_log")
public class BudgetWarningLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String budgetMonth;

    private String category;

    private BigDecimal budgetAmount;

    private BigDecimal usedAmount;

    private BigDecimal usageRate;

    /**
     * NORMAL、WARNING、DANGER、OVER
     */
    private String warningLevel;

    private String warningMessage;

    /**
     * SYSTEM、SMS、EMAIL
     */
    private String notifyType;

    /**
     * 0未通知，1已通知，2通知失败
     */
    private Integer notifyStatus;

    private LocalDateTime createTime;
}