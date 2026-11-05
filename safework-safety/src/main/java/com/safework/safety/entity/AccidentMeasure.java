package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 事故整改措施：指定责任人和期限，责任人完成后填写落实情况 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("accident_measure")
public class AccidentMeasure extends BaseEntity {
    private Long investigationId;
    private String content;
    private Long ownerId;
    private String ownerName;
    private LocalDate deadline;
    /** OPEN 进行中 / DONE 已完成（逾期是查询时算的） */
    private String status;
    private String doneNote;
    private LocalDateTime doneTime;
    private String doneBy;
    @Version
    private Integer version;

    /** 查询时计算：OPEN / OVERDUE / DONE */
    @TableField(exist = false)
    private String state;
    /** 我的整改措施列表用：所属事故 */
    @TableField(exist = false)
    private String eventTitle;
}
