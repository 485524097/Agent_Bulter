package com.test.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentChatSessionDTO {

    private Long id;

    private String sessionId;

    private String title;

    private String lastMessage;

    private LocalDateTime lastActiveTime;
}