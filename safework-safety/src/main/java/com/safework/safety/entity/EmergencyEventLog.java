package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 事件处置记录：只增不改，事后复盘用 */
@Data
@TableName("emergency_event_log")
public class EmergencyEventLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long eventId;
    /** REPORT 上报 / START 启动响应 / UPDATE 处置记录 / CLOSE 结束 */
    private String action;
    private String content;
    private Long operatorId;
    private String operatorName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
