package com.safework.safety.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全生产费用（安全投入）的规则（纯计算，方便单元测试）。
 * <pre>
 * 依据《企业安全生产费用提取和使用管理办法》（财资〔2022〕136 号）第五节 危险品生产与储存企业：
 * 提取（第二十一条）：以上一年度营业收入为依据，超额累退计算本年应提，逐月平均提取
 *   不超过 1000 万元的部分 4.5%；1000 万 ~ 1 亿元的部分 2.25%；1 亿 ~ 10 亿元的部分 0.55%；超过 10 亿元的部分 0.2%
 * 使用（第二十二条）：只能用于规定的 10 类支出，见 CATEGORIES
 * 结余：年度结余结转下年使用；当年可用 = 上年结转 + 本年应提，余额为负的年末要补提
 * 月初余额达到上一年应提金额 3 倍及以上的，可以暂停提取
 * </pre>
 * 金额单位：营业收入用万元（填报方便），其余都是元，保留两位小数。
 */
public final class CostRules {

    public record Category(String code, String label, String description) {
    }

    public static final List<Category> CATEGORY_LIST = List.of(
            new Category("FACILITY", "安全防护设施设备", "完善、改造和维护安全防护设施设备（不含“三同时”投入），如监控、通风、防火防爆、防泄漏、防静电、紧急切断等"),
            new Category("EMERGENCY", "应急救援与演练", "配备、维护、保养应急救援器材设备，应急救援队伍建设，应急预案制修订与应急演练"),
            new Category("RISK_HAZARD", "风险管控与隐患整改", "重大危险源检测评估监控，安全风险分级管控和事故隐患排查整改，安全生产信息化、智能化建设与运维"),
            new Category("INSPECTION", "安全检查评价与标准化", "安全生产检查、评估评价（不含新改扩建项目安全评价）、咨询和标准化建设"),
            new Category("PPE", "劳动防护用品", "配备和更新现场作业人员安全防护用品"),
            new Category("TRAINING", "安全教育培训", "安全生产宣传、教育、培训，从业人员发现并报告事故隐患的奖励"),
            new Category("NEW_TECH", "安全新技术新装备", "安全生产适用的新技术、新标准、新工艺、新装备的推广应用"),
            new Category("TESTING", "检测检验", "安全设施及特种设备检测检验、检定校准"),
            new Category("INSURANCE", "安全生产责任保险", "安全生产责任保险"),
            new Category("OTHER", "其他", "与安全生产直接相关的其他支出"));

    public static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORY_LIST.forEach(c -> CATEGORIES.put(c.code(), c));
    }

    /** 一档：营业收入（万元）从 from 到 to（null 表示以上）的部分，按 rate（%）提取 */
    public record Tier(long from, Long to, BigDecimal rate) {
    }

    public static final List<Tier> TIERS = List.of(
            new Tier(0, 1000L, new BigDecimal("4.5")),
            new Tier(1000, 10000L, new BigDecimal("2.25")),
            new Tier(10000, 100000L, new BigDecimal("0.55")),
            new Tier(100000, null, new BigDecimal("0.2")));

    /** 计算过程的一行：这一档的营业收入（万元）× 比例 = 金额（元） */
    public record AccrualLine(String range, BigDecimal base, BigDecimal rate, BigDecimal amount) {
    }

    public record Accrual(BigDecimal total, List<AccrualLine> lines) {
    }

    public static final String NORMAL = "NORMAL";
    /** 超过分类预算 */
    public static final String OVER = "OVER";
    /** 没有预算却有支出 */
    public static final String UNPLANNED = "UNPLANNED";

    private static final BigDecimal WAN = new BigDecimal("10000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private CostRules() {
    }

    /** 超额累退：本年应提（元）= Σ 每一档的营业收入 × 比例 */
    public static Accrual accrue(BigDecimal revenueWan) {
        List<AccrualLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (revenueWan == null || revenueWan.signum() <= 0) {
            return new Accrual(money(total), lines);
        }
        for (Tier t : TIERS) {
            BigDecimal from = BigDecimal.valueOf(t.from());
            if (revenueWan.compareTo(from) <= 0) {
                break;
            }
            BigDecimal top = t.to() == null ? revenueWan : revenueWan.min(BigDecimal.valueOf(t.to()));
            BigDecimal base = top.subtract(from);
            BigDecimal amount = money(base.multiply(WAN).multiply(t.rate()).divide(HUNDRED, 4, RoundingMode.HALF_UP));
            total = total.add(amount);
            String range = t.to() == null ? "超过 " + wan(t.from()) + " 的部分" : t.from() == 0 ? "不超过 " + wan(t.to()) + " 的部分"
                    : wan(t.from()) + " ~ " + wan(t.to()) + " 的部分";
            lines.add(new AccrualLine(range, base.stripTrailingZeros(), t.rate(), amount));
        }
        return new Accrual(money(total), lines);
    }

    /** 截至某月末按“逐月平均提取”应已提取的金额 */
    public static BigDecimal accruedByMonth(BigDecimal yearAccrue, int months) {
        int m = Math.max(0, Math.min(12, months));
        return money(yearAccrue.multiply(BigDecimal.valueOf(m)).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP));
    }

    /** 使用率（%，一位小数）；可用额度为 0 返回 null */
    public static Double rate(BigDecimal used, BigDecimal available) {
        if (available == null || available.signum() <= 0) {
            return null;
        }
        return used.multiply(new BigDecimal("1000")).divide(available, 0, RoundingMode.HALF_UP).doubleValue() / 10.0;
    }

    /** 分类状态：有预算的超了是 OVER；没预算但花了是 UNPLANNED */
    public static String categoryState(BigDecimal budget, BigDecimal used) {
        BigDecimal b = budget == null ? BigDecimal.ZERO : budget;
        if (used.signum() > 0 && b.signum() == 0) {
            return UNPLANNED;
        }
        return used.compareTo(b) > 0 ? OVER : NORMAL;
    }

    /** 时间进度（%）：统计到 asOf 这一天，占全年天数的比例；过去的年份 100，未来的年份 0 */
    public static double timeProgress(int year, LocalDate asOf) {
        if (asOf.getYear() > year) {
            return 100;
        }
        if (asOf.getYear() < year) {
            return 0;
        }
        return Math.round(asOf.getDayOfYear() * 1000.0 / asOf.lengthOfYear()) / 10.0;
    }

    /** 使用进度偏慢：过了一季度以后，使用率还不到时间进度的一半（提了不用，安全投入不到位） */
    public static boolean lagging(Double usageRate, double timeProgress) {
        return usageRate != null && timeProgress >= 25 && usageRate < timeProgress / 2;
    }

    /** 可以暂停提取：年初结余达到上一年应提金额的 3 倍及以上（上一年没有计划时不判断） */
    public static boolean canPause(BigDecimal carryOver, BigDecimal lastYearAccrue) {
        return lastYearAccrue != null && lastYearAccrue.signum() > 0 && carryOver != null
                && carryOver.compareTo(lastYearAccrue.multiply(BigDecimal.valueOf(3))) >= 0;
    }

    /** 预算提示（不拦截）：合计超过可用额度时提醒年末可能要补提 */
    public static String budgetWarning(BigDecimal budgetSum, BigDecimal available) {
        if (budgetSum.compareTo(available) > 0) {
            return "分类预算合计 " + yuan(budgetSum) + " 元，超过当年可用额度 " + yuan(available) + " 元，按规定年末要补提差额";
        }
        return null;
    }

    /** 支出日期：不能是未来，也不能早于 2000 年 */
    public static String checkExpenseDate(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            return "支出日期不能晚于今天";
        }
        if (date.getYear() < 2000) {
            return "支出日期不正确";
        }
        return null;
    }

    public static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** 1234567.8 → 1,234,567.80 */
    public static String yuan(BigDecimal v) {
        return String.format("%,.2f", v);
    }

    private static String wan(long v) {
        return v >= 10000 ? (v / 10000) + " 亿元" : v + " 万元";
    }
}
