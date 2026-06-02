package com.test.dto;

import lombok.Data;

@Data
public class UserInfoResponse {

    private Long userId;

    private String username;

    private String nickname;

    private String mobile;

    private Integer mobileVerified;
}