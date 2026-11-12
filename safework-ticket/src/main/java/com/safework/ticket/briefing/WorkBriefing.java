package com.safework.ticket.briefing;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_briefing")
public class WorkBriefing extends BaseEntity {
    private Long ticketId;
    private String ticketNo;
    private String title;
    private String contentSnapshot;
    private String status;
    private Long creatorId;
    private String creatorName;
    private LocalDateTime completedTime;
    @Version
    private Integer version;
}