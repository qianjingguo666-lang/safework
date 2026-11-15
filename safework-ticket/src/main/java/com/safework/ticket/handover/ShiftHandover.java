package com.safework.ticket.handover;

import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ShiftHandover extends BaseEntity {
    private Long ticketId;
    private String ticketNo;
    private String title;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String receiverName;
    private String siteCondition;
    private String unfinished;
    private String recheck;
    private LocalDateTime acceptedAt;
}
