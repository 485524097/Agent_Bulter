package com.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.dto.UserInfoResponse;
import com.test.entity.AppUser;
import com.test.mapper.AppUserMapper;
import com.test.service.AppUserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AppUserServiceImpl
        extends ServiceImpl<AppUserMapper, AppUser>
        implements AppUserService {

    @Override
    public AppUser getOrCreateByMobile(String mobile) {

        if (mobile == null || mobile.trim().isEmpty()) {
            throw new RuntimeException("手机号不能为空");
        }

        QueryWrapper<AppUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("mobile", mobile)
                .eq("deleted", 0);

        AppUser user = this.getOne(queryWrapper);

        if (user != null) {
            user.setMobileVerified(1);
            user.setLastLoginTime(LocalDateTime.now());
            this.updateById(user);
            return user;
        }

        user = new AppUser();
        user.setUsername("user_" + mobile);
        user.setNickname("用户" + mobile.substring(mobile.length() - 4));
        user.setMobile(mobile);
        user.setMobileVerified(1);
        user.setStatus(1);
        user.setGender(0);
        user.setLastLoginTime(LocalDateTime.now());
        user.setDeleted(0);

        this.save(user);

        return user;
    }
    @Override
    public UserInfoResponse getCurrentUserInfo(Long userId) {

        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }

        AppUser user = this.getById(userId);

        if (user == null || user.getDeleted() != null && user.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }

        UserInfoResponse response = new UserInfoResponse();

        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setMobile(user.getMobile());
        response.setMobileVerified(user.getMobileVerified());

        return response;
    }
}