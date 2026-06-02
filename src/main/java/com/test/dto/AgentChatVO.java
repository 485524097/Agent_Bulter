package com.test.dto;

import lombok.Data;

@Data
public class AgentChatVO {
//    使用token了，所以不能将id传递
//    private  Long userId;
    private String sessionId;
    private String message;
}
