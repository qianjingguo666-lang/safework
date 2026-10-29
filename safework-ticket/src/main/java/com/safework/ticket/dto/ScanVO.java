package com.safework.ticket.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫码结果：只返回现场需要知道的信息，不返回手机号、内部 ID 等敏感字段
 */
@Data
public class ScanVO {
    private String tenantName;
    private String spaceCode;
    private String spaceName;
    private String spaceType;
    private String location;
    private String deptName;
    private String responsibleName;
    private String medium;
    private String riskLevel;
    /** 当前是否有正在进行的作业 */
    private boolean working;
    /** 已批准、作业中的作业票 */
    private List<TicketBrief> activeTickets;
    /** 最近完成的作业票 */
    private List<TicketBrief> recentTickets;

    @Data
    public static class TicketBrief {
        private String ticketNo;
        private String title;
        private String status;
        private String applicantName;
        private String guardianName;
        private LocalDateTime planStartTime;
        private LocalDateTime planEndTime;
        private LocalDateTime actualStartTime;
        private LocalDateTime actualEndTime;
        private BigDecimal gasOxygen;
        private BigDecimal gasLel;
        private BigDecimal gasH2s;
        private LocalDateTime gasCheckTime;
    }
}
