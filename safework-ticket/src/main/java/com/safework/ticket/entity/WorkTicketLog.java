package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.ticket.enums.TicketAction;
import com.safework.ticket.enums.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 作业票操作记录：只增不改，用于追溯谁在什么时间做了什么 */
@Data
@TableName("work_ticket_log")
public class WorkTicketLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long ticketId;
    private TicketAction action;
    private TicketStatus fromStatus;
    private TicketStatus toStatus;
    private Long operatorId;
    private String operatorName;
    private String comment;
    /** 这一步的手写签名（PNG data URL），没签为 null；不是表字段，查询详情时填充 */
    @TableField(exist = false)
    private String signImage;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
