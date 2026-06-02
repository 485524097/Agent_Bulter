package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.UserToken;

import java.time.LocalDateTime;

public interface UserTokenService extends IService<UserToken> {

    void saveToken(Long userId,
                   String token,
                   LocalDateTime expireTime);

    void invalidToken(String token);
}