package com.safework.safety.dto;

import com.safework.safety.enums.RiskLevel;
import lombok.Data;

@Data
public class RiskQueryDTO {
    private long current = 1;
    private long size = 10;
    private String keyword;
    private RiskLevel riskLevel;
    private String pointType;
    private Long deptId;
    /** true：只看巡检逾期的 */
    private Boolean overdue;
    private Integer status;
}
