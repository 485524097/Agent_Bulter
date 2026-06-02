package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sms_send_log")
public class SmsSendLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mobile;

    private String scene;

    private String templateCode;

    private String content;

    private String codeHash;

    private String provider;

    private String providerRequestId;

    /**
     * 1成功，0失败
     */
    private Integer sendStatus;

    private String failReason;

    private String ipAddress;

    private LocalDateTime expireTime;

    /**
     * 1已使用，0未使用
     */
    private Integer used;

    private LocalDateTime createTime;
}