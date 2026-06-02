package com.test.service;

import com.test.dto.AgentPlan;
import com.test.enums.IntentType;



public interface AgentIntentService {
    IntentType decideIntent(String message, AgentPlan plan);
}