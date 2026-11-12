package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 未遂事件与安全观察记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("safety_observation")
public class SafetyObservation extends BaseEntity {
    private String observationNo;
    private String observationType;
    private String title;
    private LocalDateTime occurTime;
    private String location;
    private String description;
    private String potentialLevel;
    private String immediateAction;
    private String rootCause;
    private String correctiveAction;
    private String status;
    private Long hazardId;
    private Long reporterId;
    private String reporterName;
    private Long analystId;
    private String analystName;
    private LocalDateTime analyzedTime;
    private LocalDateTime closedTime;

    @Version
    private Integer version;
}
