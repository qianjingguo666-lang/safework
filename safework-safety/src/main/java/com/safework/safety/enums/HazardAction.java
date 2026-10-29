package com.safework.safety.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

import static com.safework.safety.enums.HazardStatus.CLOSED;
import static com.safework.safety.enums.HazardStatus.PENDING_VERIFY;
import static com.safework.safety.enums.HazardStatus.RECTIFYING;
import static com.safework.safety.enums.HazardStatus.REPORTED;
import static com.safework.safety.enums.HazardStatus.REVOKED;

/**
 * 隐患闭环状态机（和作业票同样的写法）：
 * <pre>
 *  上报 → REPORTED ──指派──▶ RECTIFYING ──提交整改──▶ PENDING_VERIFY ──验收通过──▶ CLOSED
 *           │                    ▲                         │
 *          撤销                   └────────验收不通过────────┘
 *           ▼
 *        REVOKED
 * </pre>
 */
@Getter
public enum HazardAction {
    REPORT("上报", EnumSet.noneOf(HazardStatus.class), REPORTED),
    ASSIGN("指派整改", EnumSet.of(REPORTED), RECTIFYING),
    REVOKE("撤销", EnumSet.of(REPORTED), REVOKED),
    RECTIFY("提交整改", EnumSet.of(RECTIFYING), PENDING_VERIFY),
    VERIFY_PASS("验收通过", EnumSet.of(PENDING_VERIFY), CLOSED),
    VERIFY_FAIL("验收不通过", EnumSet.of(PENDING_VERIFY), RECTIFYING);

    private final String label;
    private final Set<HazardStatus> from;
    private final HazardStatus to;

    HazardAction(String label, Set<HazardStatus> from, HazardStatus to) {
        this.label = label;
        this.from = from;
        this.to = to;
    }

    public boolean canFire(HazardStatus current) {
        return from.contains(current);
    }
}
