package com.safework.safety.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum HazardStatus {
    REPORTED("待指派"),
    RECTIFYING("整改中"),
    PENDING_VERIFY("待验收"),
    CLOSED("已闭环"),
    REVOKED("已撤销");

    private final String label;
}
