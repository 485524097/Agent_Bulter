package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.UserInfoResponse;
import com.test.entity.AppUser;

public interface AppUserService extends IService<AppUser> {

    AppUser getOrCreateByMobile(String mobile);

    UserInfoResponse getCurrentUserInfo(Long userId);
}