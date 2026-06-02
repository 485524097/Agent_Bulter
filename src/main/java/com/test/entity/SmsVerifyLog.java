package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sms_verify_log")
public class SmsVerifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String mobile;

    private String scene;

    /**
     * 1成功，0失败
     */
    private Integer verifyStatus;

    private String failReason;

    private String ipAddress;

    private LocalDateTime createTime;
}