package com.safework.ticket.sign;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import com.safework.ticket.enums.TicketAction;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 手写签名：和一条操作记录（work_ticket_log）一一对应，谁做了这个操作谁签名 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket_signature")
public class TicketSignature extends BaseEntity {
    private Long ticketId;
    private Long logId;
    private TicketAction action;
    private Long signerId;
    private String signerName;
    /** PNG 的 data URL */
    private String image;
    /** 图片的 SHA-256：存档后用来证明签名没有被替换 */
    private String imageHash;
}
