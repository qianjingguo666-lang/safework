package com.safework.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.ticket.entity.WorkTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WorkTicketMapper extends BaseMapper<WorkTicket> {
}
