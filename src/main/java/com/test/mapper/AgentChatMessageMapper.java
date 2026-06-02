package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.AgentChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentChatMessageMapper extends BaseMapper<AgentChatMessage> {
}