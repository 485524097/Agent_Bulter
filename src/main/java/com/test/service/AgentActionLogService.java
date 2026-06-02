package com.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.test.dto.AgentAction;
import com.test.entity.AgentActionLog;

import java.util.List;

public interface AgentActionLogService extends IService<AgentActionLog> {

    void saveActions(Long userId,
                     String sessionId,
                     String requestText,
                     List<AgentAction> actions);
}