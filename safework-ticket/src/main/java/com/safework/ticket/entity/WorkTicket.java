package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.enums.TicketType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 作业票（按租户隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_ticket")
public class WorkTicket extends BaseEntity {
    /** 编号，如 SX20261028-0001 */
    private String ticketNo;
    private TicketType ticketType;
    /** 关联的受限空间（受限空间作业必填） */
    private Long spaceId;
    private String spaceName;
    private String title;
    private String location;
    private String workContent;
    private String safetyMeasures;

    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;

    private Long applicantId;
    private String applicantName;
    private Long guardianId;
    private String guardianName;
    private Long approverId;
    private String approverName;

    /** 氧含量 %VOL */
    private BigDecimal gasOxygen;
    /** 可燃气体 %LEL */
    private BigDecimal gasLel;
    /** 硫化氢 mg/m³ */
    private BigDecimal gasH2s;
    private LocalDateTime gasCheckTime;

    private TicketStatus status;

    /** 乐观锁版本号：两个人同时审批时，只有一个能成功 */
    @Version
    private Integer version;
}
