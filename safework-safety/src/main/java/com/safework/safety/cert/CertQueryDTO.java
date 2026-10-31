package com.safework.safety.cert;

import lombok.Data;

@Data
public class CertQueryDTO {
    private long current = 1;
    private long size = 10;
    /** 持证人姓名或证书编号 */
    private String keyword;
    private String certType;
    /** VALID / EXPIRING / EXPIRED */
    private String state;
    private Long userId;
}
