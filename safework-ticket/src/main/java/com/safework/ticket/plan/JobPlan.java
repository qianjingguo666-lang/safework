package com.safework.ticket.plan;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_plan")
public class JobPlan extends BaseEntity {
    private String planNo;
    private String title;
    private String ticketType;
    private String location;
    private String workContent;
    private String departmentName;
    private Long responsibleId;
    private String responsibleName;
    private LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Long ticketId;
    private String ticketNo;
    private String status;
    private String remark;
    @Version
    private Integer version;
}