package com.safework.safety.accident;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事故调查处理的规则（纯计算，方便单元测试）。
 * <pre>
 * “四不放过”：事故原因未查清不放过、责任人员未处理不放过、整改措施未落实不放过、有关人员未受到教育不放过。
 * 四项都满足才能结案。
 * </pre>
 */
public final class AccidentRules {

    public static final String INVESTIGATING = "INVESTIGATING";
    public static final String CLOSED = "CLOSED";
    /** 调查中且超过调查期限（查询时算） */
    public static final String OVERDUE = "OVERDUE";

    public static final String OPEN = "OPEN";
    public static final String DONE = "DONE";

    public static final int MAX_CAUSE_LENGTH = 1000;
    public static final int MIN_CAUSE_LENGTH = 10;
    public static final int MAX_MEASURE_DAYS = 180;

    /** 责任类别 */
    public static final Map<String, String> DUTIES = ordered(
            "DIRECT", "直接责任", "MAIN", "主要责任", "LEADER", "领导责任", "MANAGEMENT", "管理责任");

    private AccidentRules() {
    }

    /**
     * 调查期限（天）：一般事故 30 天；较大及以上 60 天
     * （《生产安全事故报告和调查处理条例》规定事故调查组应当自事故发生之日起 60 日内提交调查报告，企业内部一般事故从严按 30 天）
     */
    public static int deadlineDays(String level) {
        return "GENERAL".equals(level) || level == null ? 30 : 60;
    }

    public static LocalDate defaultDeadline(String level, LocalDate occurDate) {
        return occurDate.plusDays(deadlineDays(level));
    }

    /** 调查状态：已结案 / 超期 / 调查中 */
    public static String state(String status, LocalDate deadline, LocalDate today) {
        if (CLOSED.equals(status)) {
            return CLOSED;
        }
        return deadline != null && today.isAfter(deadline) ? OVERDUE : INVESTIGATING;
    }

    /** 整改措施状态：已完成 / 逾期 / 进行中 */
    public static String measureState(String status, LocalDate deadline, LocalDate today) {
        if (DONE.equals(status)) {
            return DONE;
        }
        return today.isAfter(deadline) ? OVERDUE : OPEN;
    }

    /** 整改期限：不能早于今天，最长 180 天 */
    public static String checkMeasureDeadline(LocalDate deadline, LocalDate today) {
        if (deadline == null) {
            return "请选择整改期限";
        }
        if (deadline.isBefore(today)) {
            return "整改期限不能早于今天";
        }
        if (deadline.isAfter(today.plusDays(MAX_MEASURE_DAYS))) {
            return "整改期限最长 " + MAX_MEASURE_DAYS + " 天";
        }
        return null;
    }

    /** 整改措施提醒：截止前 3 天、1 天、当天；逾期后每 3 天一次 */
    public static boolean shouldRemind(LocalDate deadline, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, deadline);
        if (days == 3 || days == 1 || days == 0) {
            return true;
        }
        return days < 0 && (-days - 1) % 3 == 0;
    }

    /** 调查超期提醒管理员：超期第 1 天，之后每 7 天 */
    public static boolean shouldWarnOverdue(LocalDate deadline, LocalDate today) {
        long over = ChronoUnit.DAYS.between(deadline, today);
        return over >= 1 && (over - 1) % 7 == 0;
    }

    /** 原因是否算“查清”：直接原因和管理原因都要写，至少 10 个字 */
    static boolean causeFilled(String text) {
        return text != null && text.trim().length() >= MIN_CAUSE_LENGTH;
    }

    /**
     * “四不放过”检查结果
     *
     * @param key  CAUSE / PUNISH / MEASURE / EDUCATION
     * @param ok   是否满足
     * @param hint 不满足时还差什么；满足时是完成情况
     */
    public record Check(String key, String label, boolean ok, String hint) {
    }

    /**
     * 计算“四不放过”。
     *
     * @param directCause     直接原因
     * @param managementCause 管理原因
     * @param punishCount     责任处理记录数
     * @param measureTotal    整改措施数
     * @param measureDone     已完成的整改措施数
     * @param educationDate   警示教育日期
     * @param educationCount  受教育人数
     */
    public static List<Check> fourNots(String directCause, String managementCause, int punishCount,
                                       int measureTotal, int measureDone, LocalDate educationDate, Integer educationCount) {
        List<Check> list = new ArrayList<>();
        boolean cause = causeFilled(directCause) && causeFilled(managementCause);
        list.add(new Check("CAUSE", "原因未查清不放过", cause,
                cause ? "直接原因、管理原因已查明" : "请填写直接原因和管理原因（各至少 " + MIN_CAUSE_LENGTH + " 个字）"));
        list.add(new Check("PUNISH", "责任人员未处理不放过", punishCount > 0,
                punishCount > 0 ? "已处理 " + punishCount + " 人" : "请登记责任人员的处理情况"));
        boolean measure = measureTotal > 0 && measureDone == measureTotal;
        list.add(new Check("MEASURE", "整改措施未落实不放过", measure,
                measureTotal == 0 ? "请制定整改措施" : "整改措施完成 " + measureDone + "/" + measureTotal));
        boolean edu = educationDate != null && educationCount != null && educationCount > 0;
        list.add(new Check("EDUCATION", "有关人员未受到教育不放过", edu,
                edu ? "已开展警示教育，" + educationCount + " 人参加" : "请登记警示教育（日期、参加人数）"));
        return list;
    }

    /** 未满足的项，结案时提示 */
    public static String closeError(List<Check> checks) {
        List<String> miss = checks.stream().filter(c -> !c.ok()).map(Check::label).toList();
        return miss.isEmpty() ? null : "还不能结案，“四不放过”未满足：" + String.join("、", miss);
    }

    /** 结案后写进 AI 知识库的事故案例（Markdown） */
    public static String caseText(String eventNo, String title, String levelLabel, String typeLabel, String occur, String location,
                                  String description, String directCause, String indirectCause, String managementCause,
                                  List<String> measures, String lessons) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 事故案例：").append(title).append("\n\n");
        sb.append("事故编号：").append(eventNo).append("；等级：").append(levelLabel).append("；类型：").append(typeLabel)
                .append("；时间：").append(occur).append("；地点：").append(nvl(location)).append("\n\n");
        sb.append("## 事故经过\n").append(nvl(description)).append("\n\n");
        sb.append("## 直接原因\n").append(nvl(directCause)).append("\n\n");
        if (indirectCause != null && !indirectCause.isBlank()) {
            sb.append("## 间接原因\n").append(indirectCause).append("\n\n");
        }
        sb.append("## 管理原因\n").append(nvl(managementCause)).append("\n\n");
        sb.append("## 整改措施\n");
        for (int i = 0; i < measures.size(); i++) {
            sb.append(i + 1).append(". ").append(measures.get(i)).append("\n");
        }
        if (lessons != null && !lessons.isBlank()) {
            sb.append("\n## 事故教训\n").append(lessons).append("\n");
        }
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "（无）" : s.trim();
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
