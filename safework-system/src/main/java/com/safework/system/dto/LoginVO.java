package com.safework.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private Long userId;
    private String nickname;
    private Long tenantId;
    private String tenantName;
    /** 是否是运营平台账号（前端据此显示平台首页） */
    private boolean platform;
}
