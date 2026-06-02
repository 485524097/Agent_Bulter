package com.test.service;

import com.test.dto.LoginResponse;

public interface AuthService {

    LoginResponse loginBySms(String mobile, String scene, String code);

    void logout(String authorization);
}