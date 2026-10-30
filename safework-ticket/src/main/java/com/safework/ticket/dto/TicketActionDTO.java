package com.safework.ticket.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 流转动作的参数：审批意见；开工时监护人可以录入现场复测的气体数据 */
@Data
public class TicketActionDTO {
    private String comment;
    private BigDecimal gasOxygen;
    private BigDecimal gasLel;
    private BigDecimal gasH2s;
    /** 手写签名（PNG 的 data URL），可选 */
    private String signature;
}
