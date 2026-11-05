package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 职业健康检查记录。属于个人健康信息：只有本人和有 health:edit 的管理员能看 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oh_exam")
public class OhExam extends BaseEntity {
    private Long userId;
    private String userName;
    /** PRE / ON / LEAVE / EMERGENCY */
    private String examType;
    private LocalDate examDate;
    private String org;
    private String reportNo;
    /** NORMAL / OTHER / RECHECK / SUSPECT / CONTRAINDICATION */
    private String conclusion;
    /** 体检时接触的危害因素（名称快照） */
    private String factors;
    private String detail;
    private LocalDate recheckDate;
    /** NONE / OPEN / CLOSED */
    private String handleStatus;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String handleMeasure;
    private String handlerName;
    private LocalDateTime handleTime;
    private String recorderName;
}
