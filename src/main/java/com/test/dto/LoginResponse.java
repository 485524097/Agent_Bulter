package com.test.dto;

import lombok.Data;

@Data
public class LoginResponse {

    private Long userId;

    private String mobile;

    private String token;
}