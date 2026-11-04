package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 检查任务：计划每个周期生成一张，检查人逐项打勾，不合格项可以转隐患 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_task")
public class CheckTask extends BaseEntity {
    private String taskNo;
    private Long planId;
    private String planName;
    private String templateName;
    private String checkType;
    private String location;
    private Long checkerId;
    private String checkerName;
    /** 周期开始日期（同一个计划同一个周期只有一张，唯一索引） */
    private LocalDate periodStart;
    private LocalDate dueDate;
    /** PENDING / DONE */
    private String status;
    /** 检查项快照（JSON），生成任务时从模板复制 */
    private String items;
    /** 逐项结果（JSON）：[{content, standard, category, ok, note, hazardId, hazardNo}] */
    private String results;
    private Integer itemCount;
    private Integer failCount;
    private Integer hazardCount;
    private String summary;
    private LocalDateTime finishTime;
    @Version
    private Integer version;

    /** PENDING / OVERDUE / DONE，查询时计算 */
    @TableField(exist = false)
    private String state;
}
