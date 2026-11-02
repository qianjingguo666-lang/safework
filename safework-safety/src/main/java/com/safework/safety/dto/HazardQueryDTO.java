package com.safework.safety.dto;

import com.safework.safety.enums.HazardStatus;
import lombok.Data;

@Data
public class HazardQueryDTO {
    private long current = 1;
    private long size = 10;
    private String keyword;
    private HazardStatus status;
    private String level;
    /** true：只看逾期未整改的 */
    private Boolean overdue;
    /** true：只看由我整改的（手机端“待我整改”，安全员也只看自己的） */
    private Boolean mine;
}
