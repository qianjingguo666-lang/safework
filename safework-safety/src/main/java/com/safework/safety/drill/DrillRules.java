package com.safework.safety.drill;

import com.safework.safety.emergency.EmergencyRules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 应急演练规则（纯函数，有单元测试）。
 * <pre>
 * 计划 PLANNED ──签到──▶ 登记结果 DONE ──评估打分──▶ 已评估 EVALUATED
 *     └──▶ 取消 CANCELLED
 * </pre>
 * 评估参照 AQ/T 9009《生产安全事故应急演练评估规范》：8 个评估项目，桌面推演可以把不涉及的项目标为“不适用”，
 * 总分按适用项目折算成百分制。演练频次按《生产安全事故应急预案管理办法》：综合、专项预案每年至少 1 次，现场处置方案每半年至少 1 次。
 */
public final class DrillRules {

    public static final String PLANNED = "PLANNED";
    public static final String DONE = "DONE";
    public static final String EVALUATED = "EVALUATED";
    public static final String CANCELLED = "CANCELLED";
    public static final Set<String> TYPES = Set.of("TABLETOP", "LIVE");

    /** 评估项目：代码 → 名称和满分，满分合计 100 */
    public record ScoreItem(String code, String name, int max, String hint) {
    }

    public static final List<ScoreItem> SCORE_ITEMS = List.of(
            new ScoreItem("REPORT", "预警与报告", 10, "发现险情后报告是否及时、内容是否准确，预警信息是否传达到位"),
            new ScoreItem("COMMAND", "指挥与协调", 15, "是否按预案启动响应、成立现场指挥部，指令是否清晰，各小组配合是否顺畅"),
            new ScoreItem("COMM", "应急通讯", 10, "通讯联络是否畅通，信息传递是否准确、及时"),
            new ScoreItem("DISPOSAL", "现场处置", 20, "处置措施是否符合预案和操作规程，个体防护是否到位，有无冒险施救"),
            new ScoreItem("EVACUATE", "人员疏散与警戒", 15, "疏散路线和集合点是否明确，清点人数是否准确，警戒范围是否合理"),
            new ScoreItem("EQUIP", "应急装备与物资", 10, "装备物资是否齐全有效、取用是否及时，人员是否会正确使用"),
            new ScoreItem("MEDICAL", "医疗救护", 10, "伤员救护是否正确及时，与医疗机构对接是否顺畅"),
            new ScoreItem("RECOVERY", "后期处置与恢复", 10, "现场洗消、恢复生产、信息报告和总结是否到位"));

    public static final Map<String, String> ROLES = ordered("COMMAND", "指挥", "RESCUE", "抢险救援", "EVACUATE", "疏散警戒",
            "MEDICAL", "医疗救护", "SUPPORT", "后勤保障", "OBSERVE", "观摩评估");
    public static final Map<String, String> PROBLEM_CATEGORIES = ordered("PLAN", "预案本身", "EQUIPMENT", "装备物资", "TRAINING", "人员培训",
            "ORGANIZE", "组织协调", "OTHER", "其他");

    /** 至少要有几个适用的评估项目 */
    public static final int MIN_APPLICABLE = 4;
    /** 签到：计划时间前 2 小时到当天结束 */
    public static final int SIGN_BEFORE_HOURS = 2;
    /** 演练前几天提醒参演人员 */
    public static final Set<Long> REMIND_BEFORE_DAYS = Set.of(3L, 1L, 0L);
    /** 过了计划日期几天还没登记结果就提醒 */
    public static final int RECORD_LATE_DAYS = 1;
    /** 登记结果后几天还没评估就提醒 */
    public static final int EVALUATE_LATE_DAYS = 3;

    public static final String YEAR_DONE = "DONE";
    public static final String YEAR_PLANNED = "PLANNED";
    public static final String YEAR_MISSING = "MISSING";

    private DrillRules() {
    }

    public static ScoreItem item(String code) {
        return SCORE_ITEMS.stream().filter(i -> i.code().equals(code)).findFirst().orElse(null);
    }

    /** 一项的打分：null 表示不适用 */
    public record Score(String code, BigDecimal score) {
    }

    /** 评估结果：total 百分制（一位小数），applicableMax 适用项目的满分合计 */
    public record Result(BigDecimal total, int applicableMax, String grade) {
    }

    /** 校验打分：每个项目都要有（可以不适用），分数在 0 ~ 满分之间、最小 0.5 分，至少 4 个适用项目；不通过返回错误信息 */
    public static String checkScores(List<Score> scores) {
        if (scores == null || scores.size() != SCORE_ITEMS.size()) {
            return "请给全部 " + SCORE_ITEMS.size() + " 个评估项目打分（不涉及的选“不适用”）";
        }
        Set<String> seen = new java.util.HashSet<>();
        int applicable = 0;
        for (Score s : scores) {
            ScoreItem it = item(s.code());
            if (it == null || !seen.add(s.code())) {
                return "评估项目不正确";
            }
            if (s.score() == null) {
                continue;
            }
            applicable++;
            if (s.score().signum() < 0 || s.score().compareTo(BigDecimal.valueOf(it.max())) > 0) {
                return "【" + it.name() + "】的分数要在 0 ~ " + it.max() + " 之间";
            }
            if (s.score().multiply(BigDecimal.valueOf(2)).stripTrailingZeros().scale() > 0) {
                return "【" + it.name() + "】的分数最小单位是 0.5";
            }
        }
        if (applicable < MIN_APPLICABLE) {
            return "适用的评估项目至少 " + MIN_APPLICABLE + " 个";
        }
        return null;
    }

    /** 总分 = 得分合计 ÷ 适用项目满分合计 × 100（一位小数）；先调用 checkScores */
    public static Result result(List<Score> scores) {
        BigDecimal got = BigDecimal.ZERO;
        int max = 0;
        for (Score s : scores) {
            if (s.score() != null) {
                got = got.add(s.score());
                max += item(s.code()).max();
            }
        }
        BigDecimal total = max == 0 ? BigDecimal.ZERO : got.multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(max), 1, RoundingMode.HALF_UP);
        return new Result(total, max, grade(total));
    }

    /** 评估等级：90 优秀、75 良好、60 合格，以下不合格 */
    public static String grade(BigDecimal total) {
        if (total.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "优秀";
        }
        if (total.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "良好";
        }
        if (total.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "合格";
        }
        return "不合格";
    }

    /** 每年至少演练几次：现场处置方案 2 次，综合、专项 1 次 */
    public static int requiredPerYear(String planType) {
        return EmergencyRules.PLAN_ONSITE.equals(planType) ? 2 : 1;
    }

    /** 年度完成情况：完成次数够了 DONE；加上已安排的够了 PLANNED；否则 MISSING */
    public static String yearState(String planType, int done, int planned) {
        int need = requiredPerYear(planType);
        if (done >= need) {
            return YEAR_DONE;
        }
        return done + planned >= need ? YEAR_PLANNED : YEAR_MISSING;
    }

    /** 能不能签到：计划中，且在计划时间前 2 小时到当天结束之间 */
    public static boolean canSign(String status, LocalDateTime planTime, LocalDateTime now) {
        return PLANNED.equals(status) && !now.isBefore(planTime.minusHours(SIGN_BEFORE_HOURS))
                && now.toLocalDate().equals(planTime.toLocalDate());
    }

    /** 今天要不要提醒参演人员：演练前 3 / 1 / 0 天 */
    public static boolean remindParticipants(LocalDateTime planTime, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, planTime.toLocalDate());
        return REMIND_BEFORE_DAYS.contains(days);
    }

    /** 过了计划日期还没登记结果 */
    public static boolean recordLate(String status, LocalDateTime planTime, LocalDate today) {
        return PLANNED.equals(status) && ChronoUnit.DAYS.between(planTime.toLocalDate(), today) >= RECORD_LATE_DAYS;
    }

    /** 登记结果后还没评估 */
    public static boolean evaluateLate(String status, LocalDateTime actualTime, LocalDate today) {
        return DONE.equals(status) && actualTime != null && ChronoUnit.DAYS.between(actualTime.toLocalDate(), today) >= EVALUATE_LATE_DAYS;
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
