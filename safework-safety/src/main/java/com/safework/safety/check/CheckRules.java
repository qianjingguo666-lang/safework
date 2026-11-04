package com.safework.safety.check;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全检查规则（纯函数，方便单元测试）：
 * <ul>
 *   <li>检查计划按周期生成检查任务：每天 / 每周 / 每月 / 一次性；每个周期一张任务，截止日期是周期最后一天</li>
 *   <li>定时任务没执行（停机、服务器重启）时不补发积压的任务，只生成“今天所在的那个周期”</li>
 *   <li>逐项检查：结果条数必须和检查表一致；不合格项要么转隐患，要么写明现场是怎么处理的</li>
 * </ul>
 */
public final class CheckRules {

    /** 检查类型 */
    public static final Map<String, String> CHECK_TYPES = new LinkedHashMap<>();
    /** 周期 */
    public static final Map<String, String> CYCLES = new LinkedHashMap<>();
    /** 检查项的隐患类别（和隐患模块一致） */
    public static final Set<String> CATEGORIES = Set.of("设备设施", "作业环境", "人员行为", "安全管理");

    static {
        CHECK_TYPES.put("DAILY", "日常检查");
        CHECK_TYPES.put("SPECIAL", "专项检查");
        CHECK_TYPES.put("HOLIDAY", "节假日检查");
        CHECK_TYPES.put("SEASONAL", "季节性检查");
        CHECK_TYPES.put("COMPREHENSIVE", "综合检查");
        CYCLES.put("DAILY", "每天");
        CYCLES.put("WEEKLY", "每周");
        CYCLES.put("MONTHLY", "每月");
        CYCLES.put("ONCE", "一次性");
    }

    public static final String DAILY = "DAILY";
    public static final String WEEKLY = "WEEKLY";
    public static final String MONTHLY = "MONTHLY";
    public static final String ONCE = "ONCE";

    /** 任务状态：PENDING 待检查 / DONE 已完成；逾期是查询时算出来的 */
    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String OVERDUE = "OVERDUE";

    public static final int MAX_ITEMS = 50;
    /** 一次性检查没填结束日期时，给 7 天时间 */
    public static final int ONCE_DEFAULT_DAYS = 7;

    private CheckRules() {
    }

    /** 下一个周期的开始日期；一次性的没有下一次 */
    public static LocalDate next(String cycle, LocalDate start) {
        return switch (cycle) {
            case DAILY -> start.plusDays(1);
            case WEEKLY -> start.plusWeeks(1);
            case MONTHLY -> start.plusMonths(1);
            case ONCE -> null;
            default -> throw new IllegalArgumentException("检查周期不正确");
        };
    }

    /** 周期的截止日期（周期最后一天）；不能晚于计划结束日期 */
    public static LocalDate due(String cycle, LocalDate start, LocalDate planEnd) {
        LocalDate due = switch (cycle) {
            case DAILY -> start;
            case WEEKLY -> start.plusDays(6);
            case MONTHLY -> start.plusMonths(1).minusDays(1);
            case ONCE -> planEnd != null ? planEnd : start.plusDays(ONCE_DEFAULT_DAYS - 1);
            default -> throw new IllegalArgumentException("检查周期不正确");
        };
        return planEnd != null && due.isAfter(planEnd) ? planEnd : due;
    }

    /**
     * 今天应该生成哪个周期的任务：从计划的下次生成日期往后推到“包含今天”的那个周期。
     * 中间错过的周期（定时任务没执行）不补，免得一下子给人派好几张已经逾期的任务。
     *
     * @return 周期开始日期；还没到时间、或计划已经结束返回 null
     */
    public static LocalDate periodToGenerate(String cycle, LocalDate nextDate, LocalDate planEnd, LocalDate today) {
        if (nextDate == null || nextDate.isAfter(today)) {
            return null;
        }
        LocalDate d = nextDate;
        LocalDate n = next(cycle, d);
        while (n != null && !n.isAfter(today)) {
            d = n;
            n = next(cycle, d);
        }
        if (planEnd != null && d.isAfter(planEnd)) {
            return null;
        }
        return d;
    }

    /** 任务状态：待检查但过了截止日期就是逾期 */
    public static String state(String status, LocalDate due, LocalDate today) {
        if (DONE.equals(status)) {
            return DONE;
        }
        return due.isBefore(today) ? OVERDUE : PENDING;
    }

    /** 每周计划建议从周一开始：返回 date 所在周的周一（前端默认值用） */
    public static LocalDate monday(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    /** 检查表模板的检查项校验：返回错误信息，没问题返回 null */
    public static String checkItems(List<CheckItem> items) {
        if (items == null || items.isEmpty()) {
            return "检查表至少要有一个检查项";
        }
        if (items.size() > MAX_ITEMS) {
            return "检查项最多 " + MAX_ITEMS + " 条";
        }
        for (int i = 0; i < items.size(); i++) {
            CheckItem it = items.get(i);
            if (it == null || it.content() == null || it.content().isBlank()) {
                return "第 " + (i + 1) + " 个检查项没有填写内容";
            }
            if (it.content().length() > 200) {
                return "第 " + (i + 1) + " 个检查项超过 200 字";
            }
            if (it.category() != null && !CATEGORIES.contains(it.category())) {
                return "第 " + (i + 1) + " 个检查项的隐患类别不正确";
            }
        }
        long distinct = items.stream().map(x -> x.content().trim()).distinct().count();
        if (distinct < items.size()) {
            return "有重复的检查项";
        }
        return null;
    }

    /**
     * 逐项检查结果校验：条数必须和检查表一致（防止漏查）；不合格项要么转隐患，要么写明现场处理情况。
     *
     * @return 错误信息，没问题返回 null
     */
    public static String checkResults(int itemCount, List<Boolean> ok, List<Boolean> toHazard, List<String> notes) {
        if (ok == null || ok.size() != itemCount) {
            return "检查表有 " + itemCount + " 项，请逐项检查（可能刚被修改过，请刷新）";
        }
        for (int i = 0; i < itemCount; i++) {
            if (ok.get(i) == null) {
                return "第 " + (i + 1) + " 项还没有检查";
            }
            boolean hazard = toHazard != null && i < toHazard.size() && Boolean.TRUE.equals(toHazard.get(i));
            String note = notes != null && i < notes.size() ? notes.get(i) : null;
            if (!ok.get(i) && !hazard && (note == null || note.isBlank())) {
                return "第 " + (i + 1) + " 项不合格：请转为隐患，或者写明现场是怎么处理的";
            }
        }
        return null;
    }
}
