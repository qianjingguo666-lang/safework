package com.safework.system.message;

import lombok.Data;

import java.time.LocalDateTime;

/** 推送给前端的消息（SSE 实时推送、列表都用这个结构） */
@Data
public class MessageVO {
    private Long id;
    private String category;
    private String bizType;
    private Long bizId;
    private String title;
    private String content;
    private String link;
    private String senderName;
    private Integer readFlag;
    private LocalDateTime createTime;

    public static MessageVO of(SysMessage m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setCategory(m.getCategory());
        vo.setBizType(m.getBizType());
        vo.setBizId(m.getBizId());
        vo.setTitle(m.getTitle());
        vo.setContent(m.getContent());
        vo.setLink(m.getLink());
        vo.setSenderName(m.getSenderName());
        vo.setReadFlag(m.getReadFlag());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }
}
