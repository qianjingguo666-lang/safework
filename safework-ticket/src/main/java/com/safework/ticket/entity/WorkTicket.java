package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
    /** 承包商作业：承包商和作业人员（本企业自己干的为空）；改成本企业作业时要清空，所以 null 也写入 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long contractorId;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contractorName;
    /** 承包商作业人员 ID，逗号分隔 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contractorWorkerIds;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String contractorWorkerNames;
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

    /** 第几次提交审批（被驳回后重新提交 +1） */
    private Integer approvalRound;
    /** 当前审批到第几个节点（从 0 开始），不在审批中为 null（更新时 null 也要写入数据库） */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer currentNode;
    /** 当前审批节点名称（列表显示用） */
    @TableField(exist = false)
    private String currentStep;
    /** 提交时的审批流程快照（JSON）：审批中途修改流程配置，不影响已经提交的票 */
    @JsonIgnore
    private String flowSnapshot;

    /** 纸质作业票上二维码的验真码，第一次打印时生成 */
    @JsonIgnore
    private String verifyToken;

    /** 乐观锁版本号：两个人同时审批时，只有一个能成功 */
    @Version
    private Integer version;
}
