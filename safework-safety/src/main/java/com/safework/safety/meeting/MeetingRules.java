package com.safework.safety.meeting;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 班前会的规则（纯计算，方便单元测试）。
 * <pre>
 * 班组长开工前开班前会：讲当天的安全要点（可以让 AI 根据今天的作业票、风险点生成）→ 点名，记录每个人的出勤和身体、精神状态
 *   → 出席且状态良好的才能上岗；身体不适、精神疲劳、饮酒的当天不能上岗（通知安全员）→ 成员在手机上确认“已参加、清楚今天的安全要点”
 * 开展率 = 最近 N 天里开了班前会的天数 / N
 * </pre>
 */
public final class MeetingRules {

    public static final Map<String, String> SHIFTS = ordered("DAY", "白班", "MIDDLE", "中班", "NIGHT", "夜班");

    public static final String PRESENT = "PRESENT";
    public static final Map<String, String> ATTENDS = ordered(PRESENT, "出席", "LEAVE", "请假", "ABSENT", "缺席");

    public static final String GOOD = "GOOD";
    public static final Map<String, String> CONDITIONS = ordered(GOOD, "良好", "UNWELL", "身体不适", "TIRED", "精神疲劳", "DRINK", "饮酒");

    public static final int MIN_TOPICS = 10;
    public static final int MAX_TOPICS = 2000;
    /** 最多补录几天前的班前会 */
    public static final int MAX_BACKFILL_DAYS = 3;
    public static final int STAT_DAYS = 30;

    private MeetingRules() {
    }

    /** 能不能上岗：出席并且状态良好 */
    public static boolean fit(String attend, String condition) {
        return PRESENT.equals(attend) && GOOD.equals(condition);
    }

    /** 出席的人状态不好（不能上岗）；请假、缺席的不算 */
    public static boolean unfit(String attend, String condition) {
        return PRESENT.equals(attend) && !GOOD.equals(condition);
    }

    public static String checkDate(LocalDate date, LocalDate today) {
        if (date == null) {
            return "请选择日期";
        }
        if (date.isAfter(today)) {
            return "不能登记以后的班前会";
        }
        if (date.isBefore(today.minusDays(MAX_BACKFILL_DAYS))) {
            return "只能补录 " + MAX_BACKFILL_DAYS + " 天内的班前会";
        }
        return null;
    }

    public static String checkTopics(String topics) {
        int n = topics == null ? 0 : topics.trim().length();
        if (n < MIN_TOPICS) {
            return "安全要点至少写 " + MIN_TOPICS + " 个字（今天干什么活、有什么风险、要注意什么）";
        }
        if (n > MAX_TOPICS) {
            return "安全要点不超过 " + MAX_TOPICS + " 字";
        }
        return null;
    }

    /** 一条点名记录 */
    public record Roll(Long userId, String attend, String condition) {
    }

    /**
     * 点名校验：班组每个成员都要点到（不能多、不能少、不能重复），出勤和状态要是字典里的值；请假、缺席的不填状态
     */
    public static String checkRoll(Set<Long> members, List<Roll> roll) {
        if (roll == null || roll.isEmpty()) {
            return "请点名";
        }
        if (roll.stream().map(Roll::userId).distinct().count() != roll.size()) {
            return "点名有重复的人";
        }
        for (Roll r : roll) {
            if (!members.contains(r.userId())) {
                return "点名里有不是这个班组的人";
            }
            if (!ATTENDS.containsKey(r.attend())) {
                return "出勤情况不正确";
            }
            if (PRESENT.equals(r.attend()) && !CONDITIONS.containsKey(r.condition())) {
                return "请选择出席人员的身体和精神状态";
            }
        }
        if (roll.size() != members.size()) {
            return "还有 " + (members.size() - roll.size()) + " 名成员没点名";
        }
        if (roll.stream().noneMatch(r -> PRESENT.equals(r.attend()))) {
            return "没有出席的人，不能开班前会";
        }
        return null;
    }

    /** 开展率（%），days 为 0 时为空 */
    public static Integer rate(long held, long days) {
        return days <= 0 ? null : (int) Math.round(Math.min(held, days) * 100.0 / days);
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
