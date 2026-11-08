package com.safework.ticket.jsa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作业安全分析（JSA）规则（纯函数，有单元测试）。
 * <pre>
 * 草稿 DRAFT / 被驳回 REJECTED ──提交──▶ 审核中 REVIEWING ──通过──▶ 已发布 PUBLISHED ──停用──▶ 已停用 ARCHIVED
 *                                                  └──驳回──▶ REJECTED
 * </pre>
 * 每个步骤用“可能性 L × 严重度 S”（各 1~5）评估风险：1~4 低、5~9 一般、10~14 较大、15~25 重大。
 * 发布超过 1 年要复审（复制一份重新走审核，旧的停用）。
 */
public final class JsaRules {

    public static final String DRAFT = "DRAFT";
    public static final String REVIEWING = "REVIEWING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String REJECTED = "REJECTED";
    public static final String ARCHIVED = "ARCHIVED";

    public static final int MIN_STEPS = 2;
    public static final int MAX_STEPS = 30;
    public static final int REVIEW_MONTHS = 12;

    public static final Map<String, String> STATUS = ordered(DRAFT, "草稿", REVIEWING, "审核中", PUBLISHED, "已发布", REJECTED, "被驳回", ARCHIVED, "已停用");
    public static final Map<String, String> LEVELS = ordered("LOW", "低风险", "GENERAL", "一般风险", "HIGH", "较大风险", "MAJOR", "重大风险");

    /** 一个作业步骤 */
    public record Step(String step, String hazards, Integer likelihood, Integer severity, String controls) {
    }

    /** 状态动作 */
    public enum Action {
        SUBMIT("提交审核", List.of(DRAFT, REJECTED), REVIEWING),
        APPROVE("审核通过", List.of(REVIEWING), PUBLISHED),
        REJECT("驳回", List.of(REVIEWING), REJECTED),
        ARCHIVE("停用", List.of(PUBLISHED), ARCHIVED);

        private final String label;
        private final List<String> from;
        private final String to;

        Action(String label, List<String> from, String to) {
            this.label = label;
            this.from = from;
            this.to = to;
        }

        public String label() {
            return label;
        }

        public String to() {
            return to;
        }

        public boolean canFire(String status) {
            return from.contains(status);
        }
    }

    private JsaRules() {
    }

    public static boolean editable(String status) {
        return DRAFT.equals(status) || REJECTED.equals(status);
    }

    /** 风险值 L × S */
    public static int risk(Step s) {
        return s.likelihood() * s.severity();
    }

    public static String level(int risk) {
        if (risk >= 15) {
            return "MAJOR";
        }
        if (risk >= 10) {
            return "HIGH";
        }
        if (risk >= 5) {
            return "GENERAL";
        }
        return "LOW";
    }

    /** 最高风险值（没有步骤为 0） */
    public static int maxRisk(List<Step> steps) {
        return steps.stream().mapToInt(JsaRules::risk).max().orElse(0);
    }

    /** 校验步骤：2 ~ 30 步；每步要写步骤、危害、控制措施，L 和 S 在 1~5；重大风险步骤的控制措施至少写 2 条（用分号或换行分开）。不通过返回错误信息 */
    public static String checkSteps(List<Step> steps) {
        if (steps == null || steps.size() < MIN_STEPS) {
            return "至少要分 " + MIN_STEPS + " 个作业步骤";
        }
        if (steps.size() > MAX_STEPS) {
            return "作业步骤不超过 " + MAX_STEPS + " 个";
        }
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            String n = "第 " + (i + 1) + " 步";
            if (blank(s.step()) || s.step().length() > 100) {
                return n + "：请写作业步骤（不超过 100 字）";
            }
            if (blank(s.hazards()) || s.hazards().length() > 300) {
                return n + "：请写存在的危害（不超过 300 字）";
            }
            if (s.likelihood() == null || s.severity() == null || s.likelihood() < 1 || s.likelihood() > 5 || s.severity() < 1 || s.severity() > 5) {
                return n + "：可能性和严重度都要在 1 ~ 5 之间";
            }
            if (blank(s.controls()) || s.controls().length() > 500) {
                return n + "：请写控制措施（不超过 500 字）";
            }
            if ("MAJOR".equals(level(risk(s))) && measures(s.controls()) < 2) {
                return n + "是重大风险（" + risk(s) + " 分），控制措施至少写 2 条，用分号或换行分开";
            }
        }
        return null;
    }

    /** 控制措施条数：按换行、中英文分号、句号分开 */
    public static int measures(String controls) {
        if (blank(controls)) {
            return 0;
        }
        return (int) java.util.Arrays.stream(controls.split("[\\n;；。]")).map(String::trim).filter(x -> !x.isEmpty()).count();
    }

    /** 作业票能不能引用：已发布，且作业类型一致（JSA 没写类型的是通用的） */
    public static String checkUsable(String status, String jsaType, String ticketType) {
        if (!PUBLISHED.equals(status)) {
            return "只能引用已发布的 JSA";
        }
        if (jsaType != null && !jsaType.equals(ticketType)) {
            return "JSA 的作业类型和作业票不一致";
        }
        return null;
    }

    /** 发布满 1 年要复审 */
    public static boolean reviewDue(String status, LocalDateTime publishTime, LocalDate today) {
        return PUBLISHED.equals(status) && publishTime != null && !today.isBefore(publishTime.toLocalDate().plusMonths(REVIEW_MONTHS));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
