package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_login_log")
public class UserLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String mobile;

    /**
     * PASSWORD、SMS_CODE、TOKEN
     */
    private String loginType;

    /**
     * 1成功，0失败
     */
    private Integer loginStatus;

    private String failReason;

    private String ipAddress;

    private String userAgent;

    private String deviceId;

    private LocalDateTime loginTime;
}