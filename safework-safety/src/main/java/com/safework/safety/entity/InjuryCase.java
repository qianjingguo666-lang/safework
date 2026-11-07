package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 工伤案件：一个受伤的人一条 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("injury_case")
public class InjuryCase extends BaseEntity {
    private String caseNo;
    private String injuredName;
    /** 本企业员工时关联账号 */
    private Long injuredUserId;
    /** OWN 本企业 / CONTRACTOR 承包商（由承包商单位申报，本企业督促） */
    private String employerType;
    private String employerName;
    private String deptName;
    private LocalDateTime injuryTime;
    private String location;
    private String description;
    /** 受伤部位 */
    private String injuryPart;
    /** 伤害性质：割伤、骨折、中毒等 */
    private String injuryNature;
    /** 关联的事故快报 */
    private Long eventId;
    private String eventNo;
    private String status;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate applyDate;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String applyNo;
    private LocalDate decisionDate;
    private String decisionNo;
    private String decisionNote;
    /** 伤残等级 1 ~ 10，0 = 未达到等级 */
    private Integer grade;
    private LocalDate appraiseDate;
    /** 本人工资（月），算一次性伤残补助金 */
    private BigDecimal monthlyWage;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate leaveStart;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate leaveEnd;
    private Integer leaveExtended;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate returnDate;
    private LocalDate closeDate;
    private String closeNote;
    private String recorderName;
}
