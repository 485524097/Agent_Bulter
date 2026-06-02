package com.test.controller;

import com.test.dto.SmsSendVO;
import com.test.dto.SmsVerifyVO;
import com.test.service.SmsService;
import com.test.util.ResultVO;
import com.test.util.ResultVOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sms")
public class SmsController {

    @Autowired
    private SmsService smsService;

    @PostMapping("/send")
    public ResultVO sendCode(@RequestBody SmsSendVO request) {

        smsService.sendCode(request.getMobile(), request.getScene());

        return ResultVOUtil.success("验证码已发送");
    }

    @PostMapping("/verify")
    public ResultVO verifyCode(@RequestBody SmsVerifyVO request) {

        boolean success = smsService.verifyCode(
                request.getMobile(),
                request.getScene(),
                request.getCode()
        );

        if (success) {
            return ResultVOUtil.success("验证码校验成功");
        }

        return ResultVOUtil.fail("验证码错误或已过期");
    }
}