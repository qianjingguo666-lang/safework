package com.safework.ticket.briefing;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("work_briefing_attendee")
public class WorkBriefingAttendee extends BaseEntity {
    private Long briefingId;
    private Long ticketId;
    private Long participantId;
    private String participantName;
    private String participantRole;
    private LocalDateTime signedTime;
    private String signImage;
    private String signHash;
}