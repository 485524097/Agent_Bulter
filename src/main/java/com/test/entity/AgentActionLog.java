package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_action_log")
public class AgentActionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    /**
     * 动作名称：recordExpense、recordIncome、queryExpense、setBudget 等
     */
    private String actionName;

    /**
     * 是否成功：1成功，0失败
     */
    private Integer success;

    /**
     * 动作说明
     */
    private String message;

    /**
     * 用户原始输入
     */
    private String requestText;

    /**
     * 动作结果JSON，当前可先不用
     */
    private String resultData;

    /**
     * 关联业务记录ID，例如 expense_record.id，当前可先不填
     */
    private Long relatedRecordId;

    private LocalDateTime createTime;
}