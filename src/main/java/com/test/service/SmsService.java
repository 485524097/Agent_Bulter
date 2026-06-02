package com.test.service;

public interface SmsService {

    void sendCode(String mobile, String scene);

    boolean verifyCode(String mobile, String scene, String code);
}