package com.test.service.impl;

import com.test.dto.LoginResponse;
import com.test.entity.AppUser;
import com.test.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_KEY_PREFIX = "login:token:";

    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    @Autowired
    private SmsService smsService;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserLoginLogService userLoginLogService;

    @Autowired
    private UserTokenService userTokenService;

    @Override
    public LoginResponse loginBySms(String mobile, String scene, String code) {

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        boolean verified = smsService.verifyCode(mobile, scene, code);

        if (!verified) {
            userLoginLogService.saveLoginLog(
                    null,
                    mobile,
                    "SMS_CODE",
                    0,
                    "验证码错误或已过期"
            );

            throw new RuntimeException("验证码错误或已过期");
        }

        AppUser user = appUserService.getOrCreateByMobile(mobile);

        String token = UUID.randomUUID().toString().replace("-", "");

        String tokenKey = TOKEN_KEY_PREFIX + token;

        stringRedisTemplate.opsForValue().set(
                tokenKey,
                String.valueOf(user.getId()),
                TOKEN_TTL
        );
        userTokenService.saveToken(
                user.getId(),
                token,
                LocalDateTime.now().plusDays(7)
        );

        userLoginLogService.saveLoginLog(
                user.getId(),
                mobile,
                "SMS_CODE",
                1,
                null
        );

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getId());
        response.setMobile(user.getMobile());
        response.setToken(token);

        return response;
    }
    @Override
    public void logout(String authorization) {

        if (authorization == null || authorization.trim().isEmpty()) {
            throw new RuntimeException("Token不能为空");
        }

        String token = authorization.trim();

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token.trim().isEmpty()) {
            throw new RuntimeException("Token不能为空");
        }

        String tokenKey = TOKEN_KEY_PREFIX + token;

        stringRedisTemplate.delete(tokenKey);

        userTokenService.invalidToken(token);
    }

}