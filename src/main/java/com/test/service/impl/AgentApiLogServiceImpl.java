package com.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.test.entity.AgentApiLog;
import com.test.mapper.AgentApiLogMapper;
import com.test.service.AgentApiLogService;
import org.springframework.stereotype.Service;

@Service
public class AgentApiLogServiceImpl
        extends ServiceImpl<AgentApiLogMapper, AgentApiLog>
        implements AgentApiLogService {
}