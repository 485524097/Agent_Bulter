package com.test.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("financial_knowledge")
public class FinancialKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String category;

    private String tags;

    private String embedding;

    private Integer enabled;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;

    /**
     * 语义相似度分数，不对应数据库字段
     */
    @TableField(exist = false)
    private Double score;
}