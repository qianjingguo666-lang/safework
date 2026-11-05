package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 变更的操作记录（时间线）；审批记录带轮次，驳回后重新审批不算之前的 */
@Data
@TableName("moc_log")
public class MocLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long changeId;
    private String action;
    private Integer round;
    private String content;
    private Long operatorId;
    private String operatorName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
