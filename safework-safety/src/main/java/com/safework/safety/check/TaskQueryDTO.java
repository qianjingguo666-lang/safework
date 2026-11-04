package com.safework.safety.check;

import lombok.Data;

@Data
public class TaskQueryDTO {
    private long current = 1;
    private long size = 10;
    private String keyword;
    /** PENDING（含逾期）/ OVERDUE / DONE */
    private String state;
    private Long planId;
    /** true：只看我负责的（安全员也只看自己的） */
    private Boolean mine;
}
