package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 变更申请（MOC）：申请 → 风险评估 → 审批 → 实施 → 投用前安全检查 → 投用 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("moc_change")
public class MocChange extends BaseEntity {
    private String changeNo;
    private String title;
    /** PROCESS / EQUIPMENT / MATERIAL / PERSONNEL / MANAGEMENT */
    private String changeType;
    /** 1 临时变更（到期要恢复原状）/ 0 永久变更 */
    private Integer temporary;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate restoreDate;
    private String location;
    private String reason;
    private String content;
    private Long applicantId;
    private String applicantName;
    private String status;

    /** 风险评估（LEC） */
    private Double lecL;
    private Double lecE;
    private Double lecC;
    private Double lecD;
    private String riskLevel;
    private String riskAnalysis;
    private String measures;
    private String assessorName;
    private LocalDateTime assessTime;

    /** 需要几人审批；每次驳回后 round + 1，重新计数 */
    private Integer approvalsNeeded;
    private Integer approvalRound;

    private String implementNote;
    private LocalDateTime implementTime;
    /** 投用前安全检查结果（JSON：[{item, ok, note}]） */
    private String pssr;
    private String pssrBy;
    private LocalDateTime pssrTime;
    private LocalDateTime closeTime;

    /** 临时变更是否已恢复 */
    private Integer restored;
    private String restoreNote;
    private LocalDateTime restoreTime;

    @Version
    private Integer version;
}
