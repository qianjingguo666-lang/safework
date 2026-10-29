package com.safework.system.dto.platform;

import lombok.Data;

@Data
public class TenantQueryDTO {
    private long current = 1;
    private long size = 10;
    /** 企业编码或名称 */
    private String keyword;
    private Integer status;
    private Long packageId;
    /** true：只看 30 天内到期和已到期的 */
    private Boolean expiring;
}
