package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 演练参演人员：安排时指定，演练当天本人手机签到，登记结果时确认是否参加 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("emergency_drill_participant")
public class DrillParticipant extends BaseEntity {
    private Long drillId;
    private Long userId;
    private String userName;
    private String deptName;
    /** 演练角色，见 DrillRules.ROLES */
    private String role;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDateTime signTime;
    /** 登记结果时确认：1 参加 0 缺席，未确认为 null */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer attended;
}
