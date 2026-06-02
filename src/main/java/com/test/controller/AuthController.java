package com.test.controller;

import com.test.common.UserContext;
import com.test.dto.LoginBySmsVO;
import com.test.dto.LoginResponse;
import com.test.dto.UserInfoResponse;
import com.test.service.AppUserService;
import com.test.service.AuthService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserService appUserService;

    @PostMapping("/login/sms")
    public ResultVO<LoginResponse> loginBySms(@RequestBody LoginBySmsVO request) {

        LoginResponse response = authService.loginBySms(
                request.getMobile(),
                request.getScene(),
                request.getCode()
        );

        return ResultVOUtil.success(response);
    }
    @PostMapping("/logout")
    public ResultVO logout(@RequestHeader(value = "Authorization", required = false) String authorization) {

        authService.logout(authorization);

        return ResultVOUtil.success("退出登录成功");
    }
    @GetMapping("/me")
    public ResultVO<UserInfoResponse> me() {

        Long userId = UserContext.getUserId();

        UserInfoResponse response = appUserService.getCurrentUserInfo(userId);

        return ResultVOUtil.success(response);
    }
}