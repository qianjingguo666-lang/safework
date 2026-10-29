package com.safework.safety.dto;

import lombok.Data;

@Data
public class SpaceQueryDTO {
    private long current = 1;
    private long size = 10;
    /** 编号 / 名称 / 位置 */
    private String keyword;
    private String spaceType;
    private String riskLevel;
    private Integer status;
    private Long deptId;
}
