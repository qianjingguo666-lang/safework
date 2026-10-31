package com.safework.safety.contractor;

import lombok.Data;

@Data
public class ContractorQueryDTO {
    private long current = 1;
    private long size = 10;
    /** 单位名称或信用代码 */
    private String keyword;
    private String status;
}
