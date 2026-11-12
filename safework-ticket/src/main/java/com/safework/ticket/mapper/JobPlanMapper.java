package com.safework.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.ticket.plan.JobPlan;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface JobPlanMapper extends BaseMapper<JobPlan> {
}