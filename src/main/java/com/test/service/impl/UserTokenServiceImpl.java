package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.UserToken;
import com.test.mapper.UserTokenMapper;
import com.test.service.UserTokenService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserTokenServiceImpl
        extends ServiceImpl<UserTokenMapper, UserToken>
        implements UserTokenService {

    @Override
    public void saveToken(Long userId,
                          String token,
                          LocalDateTime expireTime) {

        if (userId == null || token == null || token.trim().isEmpty()) {
            return;
        }

        UserToken userToken = new UserToken();

        userToken.setUserId(userId);
        userToken.setTokenHash(token);
        userToken.setTokenType("UUID");
        userToken.setExpireTime(expireTime);
        userToken.setStatus(1);
        userToken.setDeleted(0);

        this.save(userToken);
    }

    @Override
    public void invalidToken(String token) {

        if (token == null || token.trim().isEmpty()) {
            return;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        QueryWrapper<UserToken> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("token_hash", token)
                .eq("deleted", 0)
                .eq("status", 1);

        UserToken userToken = this.getOne(queryWrapper);

        if (userToken != null) {
            userToken.setStatus(0);
            this.updateById(userToken);
        }
    }
}