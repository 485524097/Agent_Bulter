package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.entity.UserLoginLog;

public interface UserLoginLogService extends IService<UserLoginLog> {

    void saveLoginLog(Long userId,
                      String mobile,
                      String loginType,
                      Integer loginStatus,
                      String failReason);
}