package com.safework.ticket.service;

import com.safework.common.exception.BizException;
import com.safework.ticket.entity.WorkTicket;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 气体检测校验。
 * 注意：这里是简化规则，用于演示业务校验的写法。真实项目应按 GB 30871-2022 等标准，
 * 区分作业类型、介质，并把阈值做成可配置项。
 */
public final class GasChecker {

    private static final BigDecimal O2_MIN = new BigDecimal("19.5");
    private static final BigDecimal O2_MAX = new BigDecimal("23.5");
    private static final BigDecimal LEL_MAX = new BigDecimal("10");
    private static final BigDecimal H2S_MAX = new BigDecimal("10");
    /** 气体检测结果的有效期：开始作业前 30 分钟内 */
    public static final Duration VALID_DURATION = Duration.ofMinutes(30);

    private GasChecker() {
    }

    /** 检查数据完整且在合格范围内 */
    public static void checkValues(WorkTicket t) {
        if (t.getGasOxygen() == null || t.getGasLel() == null || t.getGasCheckTime() == null) {
            throw new BizException(t.getTicketType().getLabel() + "必须填写气体检测数据（氧含量、可燃气体、检测时间）");
        }
        if (t.getGasOxygen().compareTo(O2_MIN) < 0 || t.getGasOxygen().compareTo(O2_MAX) > 0) {
            throw new BizException("氧含量不合格：" + t.getGasOxygen() + "%，应在 19.5% ~ 23.5% 之间");
        }
        if (t.getGasLel().compareTo(LEL_MAX) > 0) {
            throw new BizException("可燃气体浓度不合格：" + t.getGasLel() + "%LEL，不应超过 10%LEL");
        }
        if (t.getGasH2s() != null && t.getGasH2s().compareTo(H2S_MAX) > 0) {
            throw new BizException("硫化氢浓度不合格：" + t.getGasH2s() + "mg/m³，不应超过 10mg/m³");
        }
        if (t.getGasCheckTime().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new BizException("气体检测时间不能晚于当前时间");
        }
    }

    /** 开工时额外检查：检测结果必须在 30 分钟内 */
    public static void checkFresh(WorkTicket t) {
        checkValues(t);
        if (t.getGasCheckTime().isBefore(LocalDateTime.now().minus(VALID_DURATION))) {
            throw new BizException("气体检测已超过 30 分钟，请现场复测后再开工");
        }
    }
}
