package com.safework.ai.dto;

import lombok.Data;

@Data
public class ChatLogQueryDTO {
    private long current = 1;
    private long size = 20;
    private String bizType;
    /** 0：只看知识库没命中的问题 */
    private Integer hit;
    private String keyword;
}
