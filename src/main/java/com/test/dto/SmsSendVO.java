package com.test.dto;

import lombok.Data;

@Data
public class SmsSendVO {

    private String mobile;

    /**
     * 场景：LOGIN、REGISTER、RESET_PASSWORD
     */
    private String scene;
}