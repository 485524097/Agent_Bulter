package com.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.test.entity.SmsSendLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SmsSendLogMapper extends BaseMapper<SmsSendLog> {
}