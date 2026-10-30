package com.safework.ticket.approval;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import com.safework.ticket.enums.TicketType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 某种作业类型的审批流程配置（每个企业每种作业类型一条；没有配置时用默认流程） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket_flow")
public class TicketFlow extends BaseEntity {
    private TicketType ticketType;
    /** 节点列表（JSON） */
    private String nodes;
    private String updaterName;
}
