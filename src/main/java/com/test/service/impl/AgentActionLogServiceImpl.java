package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.dto.AgentAction;
import com.test.entity.AgentActionLog;
import com.test.mapper.AgentActionLogMapper;
import com.test.service.AgentActionLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentActionLogServiceImpl
        extends ServiceImpl<AgentActionLogMapper, AgentActionLog>
        implements AgentActionLogService {

    @Override
    public void saveActions(Long userId,
                            String sessionId,
                            String requestText,
                            List<AgentAction> actions) {

        if (userId == null || actions == null || actions.isEmpty()) {
            return;
        }

        for (AgentAction action : actions) {
            if (action == null) {
                continue;
            }

            AgentActionLog log = new AgentActionLog();

            log.setUserId(userId);
            log.setSessionId(sessionId);
            log.setActionName(action.getName());
            log.setSuccess(Boolean.TRUE.equals(action.getSuccess()) ? 1 : 0);
            log.setMessage(action.getMessage());
            log.setRequestText(requestText);

            this.save(log);
        }
    }
}