package com.safework.ticket.dto;

import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.enums.TicketType;
import lombok.Data;

@Data
public class TicketQueryDTO {
    private long current = 1;
    private long size = 10;
    private TicketStatus status;
    private TicketType ticketType;
    /** 按编号、名称、地点模糊搜索 */
    private String keyword;
}
