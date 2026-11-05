package com.safework.ticket.kpi;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 安全目标与绩效考核的规则（纯计算，方便单元测试）。
 * <pre>
 * 年度目标：每个指标一个目标值 + 权重（合计 100）。实际值由系统从各模块自动统计，不用手填：
 *   比例类（越高越好）：分子 / 分母，例如隐患按期整改率 = 按期整改的 / 到期的
 *   次数类（越低越好）：例如有人员伤亡的事故起数
 * 亮灯：比例类 ≥ 目标 绿；≥ 目标 × 90% 黄；否则红。次数类 ≤ 目标 绿，否则红。分母为 0 的不亮灯、不计分
 * 得分：比例类 权重 × min(1, 实际 / 目标)；次数类 达标得满分、否则 0；总分 = 得分合计 / 参与计分的权重合计 × 100
 * </pre>
 */
public final class KpiRules {

    public static final String HIGHER = "HIGHER";
    public static final String LOWER = "LOWER";

    public static final String GREEN = "GREEN";
    public static final String YELLOW = "YELLOW";
    public static final String RED = "RED";
    public static final String NO_DATA = "NO_DATA";

    /**
     * 指标定义
     *
     * @param snapshot 只能看“现在”的指标（例如设备检验及时率），没有按月趋势
     */
    public record Metric(String code, String label, String unit, String direction, double defaultTarget, int defaultWeight,
                         String source, String formula, boolean snapshot) {
    }

    public static final List<Metric> METRICS = List.of(
            new Metric("HAZARD_ONTIME", "隐患按期整改率", "%", HIGHER, 95, 20, "隐患排查",
                    "整改期限在统计期内且已到期的隐患中，按期提交整改的比例", false),
            new Metric("HAZARD_CLOSE", "隐患闭环率", "%", HIGHER, 90, 10, "隐患排查",
                    "统计期内上报的隐患（不含撤销）中，已验收闭环的比例", false),
            new Metric("CHECK_ONTIME", "安全检查按时完成率", "%", HIGHER, 95, 15, "安全检查",
                    "截止日期在统计期内且已到期的检查任务中，按时完成的比例", false),
            new Metric("MEETING_RATE", "班前会开展率", "%", HIGHER, 90, 10, "班前会",
                    "启用班组从成立那天起每天应开一次，开了班前会的班组·天数 / 应开的班组·天数", false),
            new Metric("TRAINING_PASS", "培训考试合格率", "%", HIGHER, 90, 10, "培训考试",
                    "统计期内交卷的考试中，合格的比例", false),
            new Metric("RESP_SIGN", "安全责任书签署率", "%", HIGHER, 100, 10, "安全生产责任制",
                    "本年度已发布的责任书中，已签署的比例（到统计期末累计）", false),
            new Metric("EQUIP_ONTIME", "特种设备检验及时率", "%", HIGHER, 100, 10, "设备设施",
                    "在用设备中，没有检验逾期、没有漏检的比例（当前）", true),
            new Metric("ACCIDENT", "伤亡事故起数", "起", LOWER, 0, 15, "应急管理",
                    "统计期内发生的有人员伤亡（死亡、重伤、轻伤）的事故起数", false));

    public static final Map<String, Metric> BY_CODE = METRICS.stream()
            .collect(java.util.stream.Collectors.toMap(Metric::code, m -> m, (a, b) -> a, java.util.LinkedHashMap::new));

    private KpiRules() {
    }

    /** 比例（%，一位小数）；分母为 0 返回 null */
    public static Double rate(long part, long total) {
        return total <= 0 ? null : Math.round(part * 1000.0 / total) / 10.0;
    }

    /** 亮灯 */
    public static String light(String direction, Double actual, double target) {
        if (actual == null) {
            return NO_DATA;
        }
        if (LOWER.equals(direction)) {
            return actual <= target ? GREEN : RED;
        }
        if (actual >= target) {
            return GREEN;
        }
        return actual >= target * 0.9 ? YELLOW : RED;
    }

    /** 单项得分（满分 = 权重），保留一位小数；没数据返回 null（不参与计分） */
    public static Double score(String direction, Double actual, double target, int weight) {
        if (actual == null) {
            return null;
        }
        double ratio;
        if (LOWER.equals(direction)) {
            ratio = actual <= target ? 1 : 0;
        } else {
            ratio = target <= 0 ? 1 : Math.min(1, actual / target);
        }
        return Math.round(weight * ratio * 10) / 10.0;
    }

    /** 总分：得分合计 / 参与计分的权重合计 × 100 */
    public static Double total(List<Double> scores, List<Integer> weights) {
        double got = 0;
        int full = 0;
        for (int i = 0; i < scores.size(); i++) {
            if (scores.get(i) != null) {
                got += scores.get(i);
                full += weights.get(i);
            }
        }
        return full == 0 ? null : Math.round(got * 1000.0 / full) / 10.0;
    }

    /** 目标校验：比例类 0~100，次数类 ≥ 0；权重 0~100，合计必须是 100 */
    public static String checkTargets(Map<String, Double> targets, Map<String, Integer> weights) {
        int sum = 0;
        for (Metric m : METRICS) {
            Double t = targets.get(m.code());
            Integer w = weights.get(m.code());
            if (t == null || w == null) {
                return "请填写“" + m.label() + "”的目标值和权重";
            }
            if (t < 0 || (HIGHER.equals(m.direction()) && t > 100)) {
                return "“" + m.label() + "”的目标值不正确";
            }
            if (w < 0 || w > 100) {
                return "“" + m.label() + "”的权重要在 0 ~ 100 之间";
            }
            sum += w;
        }
        return sum == 100 ? null : "权重合计要等于 100（现在是 " + sum + "）";
    }

    /** 统计区间 [from, to)：年初到“今天”或年底（取早的） */
    public static LocalDate[] yearRange(int year, LocalDate today) {
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate end = from.plusYears(1);
        LocalDate to = today.plusDays(1).isBefore(end) ? today.plusDays(1) : end;
        return new LocalDate[]{from, to.isBefore(from) ? from : to};
    }

    /** 某月的统计区间，未来的月份返回 null；当月截到今天 */
    public static LocalDate[] monthRange(int year, int month, LocalDate today) {
        LocalDate from = LocalDate.of(year, month, 1);
        if (from.isAfter(today)) {
            return null;
        }
        LocalDate end = from.plusMonths(1);
        return new LocalDate[]{from, today.plusDays(1).isBefore(end) ? today.plusDays(1) : end};
    }

    /** 比例：在 [from, to) 里的、满足 counted 的里面，满足 ok 的比例 */
    public static <T> Double ratio(List<T> rows, Predicate<T> counted, Predicate<T> ok) {
        long total = 0;
        long good = 0;
        for (T r : rows) {
            if (counted.test(r)) {
                total++;
                if (ok.test(r)) {
                    good++;
                }
            }
        }
        return rate(good, total);
    }

    /** 两个日期之间每一天 */
    public static List<LocalDate> days(LocalDate from, LocalDate to) {
        List<LocalDate> list = new ArrayList<>();
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
            list.add(d);
        }
        return list;
    }
}
