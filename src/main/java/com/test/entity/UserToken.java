package com.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_token")
public class UserToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 访问 Token 摘要，当前阶段可先直接存 token
     */
    private String tokenHash;

    private String refreshTokenHash;

    private String tokenType;

    private String deviceId;

    private String deviceName;

    private String ipAddress;

    private LocalDateTime expireTime;

    private LocalDateTime refreshExpireTime;

    /**
     * 1有效，0失效
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}