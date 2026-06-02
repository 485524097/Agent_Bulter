package com.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentChatMessageDTO {

    private Long id;

    private String role;

    private String content;

    private String intent;

    private String finalIntent;

    private LocalDateTime createTime;
}