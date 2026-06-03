package com.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserNotificationDTO {

    private Long id;

    private String title;

    private String content;

    private String notifyType;

    private Long businessId;

    /**
     * 0未读，1已读
     */
    private Integer readStatus;

    private LocalDateTime createdTime;
}