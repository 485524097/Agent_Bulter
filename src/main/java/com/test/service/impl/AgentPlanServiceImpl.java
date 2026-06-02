package com.test.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.test.dto.AgentPlan;
import com.test.service.AIService;
import com.test.service.AgentPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AgentPlanServiceImpl implements AgentPlanService {

    @Autowired
    private AIService aiService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public AgentPlan parsePlan(String sessionId, String message) {
        try {
            String json = this.aiService.plan(sessionId, message);
            System.out.println("AI Agent 计划结果：" + json);
            json = cleanJson(json);
            return this.objectMapper.readValue(json, AgentPlan.class);
        } catch (Exception e) {
            AgentPlan plan = new AgentPlan();
            plan.setIntent(null);
            plan.setValid(false);
            plan.setReason("AI解析失败：" + e.getMessage());
            return plan;
        }
    }

    private String cleanJson(String json) {
        if (json == null) {
            return "{}";
        }

        json = json.trim();
        json = json.replace("```json", "");
        json = json.replace("```", "");
        json = json.trim();

        int start = json.indexOf("{");
        int end = json.lastIndexOf("}");

        if (start >= 0 && end >= 0 && end > start) {
            return json.substring(start, end + 1);
        }

        return json;
    }
}