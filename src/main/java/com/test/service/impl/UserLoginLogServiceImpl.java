package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.UserLoginLog;
import com.test.mapper.UserLoginLogMapper;
import com.test.service.UserLoginLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserLoginLogServiceImpl
        extends ServiceImpl<UserLoginLogMapper, UserLoginLog>
        implements UserLoginLogService {

    @Override
    public void saveLoginLog(Long userId,
                             String mobile,
                             String loginType,
                             Integer loginStatus,
                             String failReason) {

        UserLoginLog log = new UserLoginLog();

        log.setUserId(userId);
        log.setMobile(mobile);
        log.setLoginType(loginType);
        log.setLoginStatus(loginStatus);
        log.setFailReason(failReason);
        log.setLoginTime(LocalDateTime.now());

        this.save(log);
    }
}