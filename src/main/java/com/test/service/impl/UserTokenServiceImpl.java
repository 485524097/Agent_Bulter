package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.UserToken;
import com.test.mapper.UserTokenMapper;
import com.test.service.UserTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserTokenServiceImpl
        extends ServiceImpl<UserTokenMapper, UserToken>
        implements UserTokenService {

    private static final String TOKEN_KEY_PREFIX = "login:token:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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

    @Override
    public Long getUserIdByToken(String token) {

        if (token == null || token.trim().isEmpty()) {
            return null;
        }

        token = token.trim();

        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        if (token.isEmpty()) {
            return null;
        }

        // 1. 优先查 Redis，和 TokenInterceptor 保持一致
        String userIdStr = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);

        if (userIdStr != null && !userIdStr.trim().isEmpty()) {
            return Long.valueOf(userIdStr);
        }

        // 2. Redis 没有时，再兜底查数据库
        QueryWrapper<UserToken> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("token_hash", token)
                .eq("deleted", 0)
                .eq("status", 1)
                .gt("expire_time", LocalDateTime.now());

        UserToken userToken = this.getOne(queryWrapper);

        if (userToken == null) {
            return null;
        }

        return userToken.getUserId();
    }
}