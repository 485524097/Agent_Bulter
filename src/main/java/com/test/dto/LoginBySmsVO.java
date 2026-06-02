package com.test.dto;

import lombok.Data;

@Data
public class LoginBySmsVO {

    private String mobile;

    private String code;

    /**
     * 默认 LOGIN
     */
    private String scene;
}