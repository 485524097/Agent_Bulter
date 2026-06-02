package com.test.service;

import com.test.dto.AgentPlan;

public interface AgentPlanService {
    AgentPlan parsePlan(String sessionId, String message);
}
