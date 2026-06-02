package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_api_log")
public class AgentApiLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private String apiName;

    private String requestUri;

    private String requestMethod;

    private String requestContent;

    /**
     * 1 成功，0 失败
     */
    private Integer success;

    private Long costMs;

    private String errorMsg;

    private LocalDateTime createdTime;
}