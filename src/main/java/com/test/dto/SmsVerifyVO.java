package com.test.dto;

import lombok.Data;

@Data
public class SmsVerifyVO {

    private String mobile;

    private String scene;

    private String code;
}