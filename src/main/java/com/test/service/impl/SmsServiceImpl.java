package com.test.service.impl;

import com.test.entity.SmsSendLog;
import com.test.entity.SmsVerifyLog;
import com.test.mapper.SmsSendLogMapper;
import com.test.mapper.SmsVerifyLogMapper;
import com.test.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Random;

@Service
public class SmsServiceImpl implements SmsService {


    private static final String CODE_KEY_PREFIX = "sms:code:";

    private static final String COOLDOWN_KEY_PREFIX = "sms:cooldown:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SmsSendLogMapper smsSendLogMapper;

    @Autowired
    private SmsVerifyLogMapper smsVerifyLogMapper;

    @Override
    public void sendCode(String mobile, String scene) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        String cooldownKey = buildCooldownKey(scene, mobile);

        Boolean hasCooldown = stringRedisTemplate.hasKey(cooldownKey);
        if (Boolean.TRUE.equals(hasCooldown)) {
            throw new RuntimeException("验证码发送过于频繁，请稍后再试");
        }

        String code = generateCode();

        String codeKey = buildCodeKey(scene, mobile);

        stringRedisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
        stringRedisTemplate.opsForValue().set(cooldownKey, "1", COOLDOWN_TTL);

        SmsSendLog sendLog = new SmsSendLog();

        sendLog.setMobile(mobile);
        sendLog.setScene(scene);
        sendLog.setTemplateCode("MOCK_LOGIN_CODE");
        sendLog.setContent("模拟短信验证码");
        sendLog.setCodeHash(code);
        sendLog.setProvider("MOCK");
        sendLog.setProviderRequestId(null);
        sendLog.setSendStatus(1);
        sendLog.setFailReason(null);
        sendLog.setExpireTime(LocalDateTime.now().plusMinutes(5));
        sendLog.setUsed(0);

        smsSendLogMapper.insert(sendLog);

        // 第一版先模拟发送，后面再接阿里云短信
        System.out.println("短信验证码发送成功，mobile=" + mobile + "，scene=" + scene + "，code=" + code);
    }

    @Override
    public boolean verifyCode(String mobile, String scene, String code) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("验证码不能为空");
        }

        if (scene == null || scene.trim().isEmpty()) {
            scene = "LOGIN";
        }

        String codeKey = buildCodeKey(scene, mobile);

        String redisCode = stringRedisTemplate.opsForValue().get(codeKey);

        boolean success = false;
        String failReason = null;

        if (redisCode == null) {
            success = false;
            failReason = "验证码已过期或不存在";
        } else if (!redisCode.equals(code)) {
            success = false;
            failReason = "验证码错误";
        } else {
            success = true;
        }

        SmsVerifyLog verifyLog = new SmsVerifyLog();
        verifyLog.setMobile(mobile);
        verifyLog.setScene(scene);
        verifyLog.setVerifyStatus(success ? 1 : 0);
        verifyLog.setFailReason(failReason);

        smsVerifyLogMapper.insert(verifyLog);

        if (success) {
            stringRedisTemplate.delete(codeKey);
        }

        return success;
    }
    private String buildCodeKey(String scene, String mobile) {
        return CODE_KEY_PREFIX + scene + ":" + mobile;
    }

    private String buildCooldownKey(String scene, String mobile) {
        return COOLDOWN_KEY_PREFIX + scene + ":" + mobile;
    }

    private String generateCode() {
        int code = new Random().nextInt(900000) + 100000;
        return String.valueOf(code);
    }
}