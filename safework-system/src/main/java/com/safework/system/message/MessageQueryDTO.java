package com.safework.system.message;

import lombok.Data;

@Data
public class MessageQueryDTO {
    private long current = 1;
    private long size = 10;
    private String category;
    /** 0 只看未读 1 只看已读，空为全部 */
    private Integer readFlag;
}
