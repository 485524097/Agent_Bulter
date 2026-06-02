package com.test.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentChatResponse {

    private String sessionId;

    private String reply;

    private List<AgentAction> actions;
}