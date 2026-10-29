package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.safety.enums.HazardAction;
import com.safework.safety.enums.HazardStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hazard_log")
public class HazardLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long hazardId;
    private HazardAction action;
    private HazardStatus fromStatus;
    private HazardStatus toStatus;
    private Long operatorId;
    private String operatorName;
    private String comment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
