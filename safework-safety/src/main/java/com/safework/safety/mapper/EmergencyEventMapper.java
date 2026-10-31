package com.safework.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.safety.entity.EmergencyEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmergencyEventMapper extends BaseMapper<EmergencyEvent> {
}
