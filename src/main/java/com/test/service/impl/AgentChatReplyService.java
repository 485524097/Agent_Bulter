package com.test.service.impl;

import com.test.dto.AgentAction;
import com.test.dto.AgentChatResponse;
import com.test.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentChatReplyService {

    @Autowired
    private AIService aiService;

    public AgentChatResponse chat(String sessionId,
                                  String message,
                                  List<AgentAction> actions) {

        AgentChatResponse response = new AgentChatResponse();

        try {
            String reply = aiService.chat(sessionId, message);

            response.setReply(reply);
            response.setActions(actions);

            return response;
        } catch (Exception e) {
//            e.printStackTrace();
            response.setReply("刚有点走神了，你可以再说一遍吗？");
            response.setActions(actions);
            return response;
        }
    }
}