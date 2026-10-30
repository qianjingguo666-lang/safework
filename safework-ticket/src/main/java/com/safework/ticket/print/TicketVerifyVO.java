package com.safework.ticket.print;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 扫码验真（匿名）：只返回核对票面需要的信息，不返回签名图片、审批意见、联系方式
 */
public record TicketVerifyVO(String tenantName, String ticketNo, String typeLabel, String title, String location,
                             String spaceName, String status, String statusLabel, TicketVerdict verdict,
                             String applicantName, String guardianName,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime planStartTime,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime planEndTime,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime actualStartTime,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime actualEndTime,
                             BigDecimal gasOxygen, BigDecimal gasLel, BigDecimal gasH2s,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime gasCheckTime,
                             List<Step> steps,
                             @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime checkTime) {

    /** 一步操作：谁、什么时候、有没有手写签名 */
    public record Step(String action, String operatorName, boolean signed,
                       @JsonFormat(pattern = "yyyy-MM-dd HH:mm") LocalDateTime time) {
    }
}
