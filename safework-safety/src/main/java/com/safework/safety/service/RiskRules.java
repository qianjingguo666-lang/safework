package com.safework.safety.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

/** 风险管控的规则（纯计算，方便单元测试） */
public final class RiskRules {

    public static final String OVERDUE = "OVERDUE";
    public static final String TODAY = "TODAY";
    public static final String UPCOMING = "UPCOMING";

    private RiskRules() {
    }

    /**
     * 管控措施：一行一条，去掉行首的序号（“1.”“2、”“（3）”“- ”），巡检时逐条确认。
     */
    public static List<String> measures(String text) {
        if (text == null) {
            return List.of();
        }
        return Arrays.stream(text.split("\\r?\\n"))
                .map(s -> s.replaceFirst("^\\s*([(（]?\\d{1,2}[)）.．、]|[-•*·])\\s*", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 巡检状态：已逾期 / 今天到期 / 未到期 */
    public static String state(LocalDateTime next, LocalDateTime now) {
        if (next == null || next.isBefore(now)) {
            return OVERDUE;
        }
        return next.toLocalDate().equals(now.toLocalDate()) ? TODAY : UPCOMING;
    }

    /** 距离下次巡检还有几天（逾期为负数） */
    public static long daysLeft(LocalDateTime next, LocalDate today) {
        return next == null ? 0 : ChronoUnit.DAYS.between(today, next.toLocalDate());
    }
}
