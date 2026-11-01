package com.safework.safety.equipment;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备设施规则（纯函数，方便单元测试）：
 * <ul>
 *   <li>设备类型决定默认检验周期和最长周期（法规要求的上限），周期可以比默认短，不能比上限长</li>
 *   <li>检验不合格：设备自动停用，并可以一键转隐患；最近一次检验合格且没有逾期，才能重新启用</li>
 *   <li>到期提醒：剩 30 天、7 天、到期当天各提醒一次；逾期后每 7 天提醒一次</li>
 * </ul>
 */
public final class EquipmentRules {

    /** 设备类型：所属大类、默认检验周期（月）、最长周期（月） */
    public record TypeInfo(String category, String label, int defaultMonths, int maxMonths, boolean special) {
    }

    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();
    public static final Map<String, TypeInfo> TYPES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("SPECIAL", "特种设备");
        CATEGORIES.put("ACCESSORY", "安全附件");
        CATEGORIES.put("DETECTOR", "检测报警仪器");
        CATEGORIES.put("FIRE", "消防设施");
        CATEGORIES.put("OTHER", "其他");
        // 特种设备：定期检验（压力容器一般 3 ~ 6 年，起重机械 2 年，场内机动车辆 1 年）
        TYPES.put("PRESSURE_VESSEL", new TypeInfo("SPECIAL", "压力容器", 36, 72, true));
        TYPES.put("PRESSURE_PIPE", new TypeInfo("SPECIAL", "压力管道", 36, 72, true));
        TYPES.put("BOILER", new TypeInfo("SPECIAL", "锅炉", 12, 12, true));
        TYPES.put("CRANE", new TypeInfo("SPECIAL", "起重机械", 24, 24, true));
        TYPES.put("FORKLIFT", new TypeInfo("SPECIAL", "场（厂）内机动车辆", 12, 12, true));
        // 安全附件：安全阀每年校验，压力表每半年检定
        TYPES.put("SAFETY_VALVE", new TypeInfo("ACCESSORY", "安全阀", 12, 12, false));
        TYPES.put("PRESSURE_GAUGE", new TypeInfo("ACCESSORY", "压力表", 6, 6, false));
        TYPES.put("RUPTURE_DISC", new TypeInfo("ACCESSORY", "爆破片", 24, 36, false));
        // 检测报警仪器：可燃 / 有毒气体报警器每年检定
        TYPES.put("COMBUSTIBLE_DETECTOR", new TypeInfo("DETECTOR", "可燃气体报警器", 12, 12, false));
        TYPES.put("TOXIC_DETECTOR", new TypeInfo("DETECTOR", "有毒气体报警器", 12, 12, false));
        TYPES.put("PORTABLE_DETECTOR", new TypeInfo("DETECTOR", "便携式气体检测仪", 12, 12, false));
        // 消防设施
        TYPES.put("EXTINGUISHER", new TypeInfo("FIRE", "灭火器", 12, 12, false));
        TYPES.put("FIRE_HYDRANT", new TypeInfo("FIRE", "消火栓", 12, 12, false));
        TYPES.put("BREATHING_APPARATUS", new TypeInfo("FIRE", "正压式空气呼吸器", 12, 12, false));
        TYPES.put("OTHER", new TypeInfo("OTHER", "其他", 12, 60, false));
    }

    public static final String IN_USE = "IN_USE";
    public static final String STOPPED = "STOPPED";
    public static final String SCRAPPED = "SCRAPPED";
    public static final Map<String, String> STATUS_LABELS = Map.of(IN_USE, "在用", STOPPED, "停用", SCRAPPED, "报废");

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    /** 检验状态 */
    public static final String NEVER = "NEVER";
    public static final String OVERDUE = "OVERDUE";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String OK = "OK";
    /** 停用、报废的不算检验状态 */
    public static final String NOT_IN_USE = "NOT_IN_USE";
    public static final int WARN_DAYS = 30;

    private EquipmentRules() {
    }

    /** 检验周期：没填用默认值；不能超过上限 */
    public static int cycle(String type, Integer months) {
        TypeInfo t = TYPES.get(type);
        if (t == null) {
            throw new IllegalArgumentException("设备类型不正确");
        }
        if (months == null) {
            return t.defaultMonths();
        }
        if (months < 1 || months > t.maxMonths()) {
            throw new IllegalArgumentException(t.label() + "的检验周期是 1 ~ " + t.maxMonths() + " 个月");
        }
        return months;
    }

    public static LocalDate nextInspect(LocalDate inspectDate, int cycleMonths) {
        return inspectDate == null ? null : inspectDate.plusMonths(cycleMonths);
    }

    public static String state(String status, LocalDate nextInspect, LocalDate today) {
        if (!IN_USE.equals(status)) {
            return NOT_IN_USE;
        }
        if (nextInspect == null) {
            return NEVER;
        }
        if (nextInspect.isBefore(today)) {
            return OVERDUE;
        }
        return ChronoUnit.DAYS.between(today, nextInspect) <= WARN_DAYS ? DUE_SOON : OK;
    }

    /** 状态变更是否允许；重新启用要求最近一次检验合格且没有逾期 */
    public static String checkStatusChange(String from, String to, String lastResult, LocalDate nextInspect, LocalDate today) {
        if (SCRAPPED.equals(from)) {
            return "已报废的设备不能再变更状态";
        }
        if (from.equals(to)) {
            return "设备已经是" + STATUS_LABELS.get(to) + "状态";
        }
        if (IN_USE.equals(to)) {
            if (FAIL.equals(lastResult)) {
                return "最近一次检验不合格，整改后重新检验合格才能启用";
            }
            if (nextInspect != null && nextInspect.isBefore(today)) {
                return "检验已逾期（应于 " + nextInspect + " 前检验），检验合格后才能启用";
            }
        }
        return null;
    }

    /** 今天要不要提醒：剩 30、7 天、到期当天；逾期后每 7 天一次 */
    public static boolean shouldRemind(LocalDate nextInspect, LocalDate today) {
        if (nextInspect == null) {
            return false;
        }
        long left = ChronoUnit.DAYS.between(today, nextInspect);
        return left == 30 || left == 7 || left == 0 || (left < 0 && -left % 7 == 0);
    }

    /** 不合格转隐患：特种设备、安全附件、报警仪器不合格按重大隐患 */
    public static String hazardLevel(String type) {
        TypeInfo t = TYPES.get(type);
        return t != null && !"FIRE".equals(t.category()) && !"OTHER".equals(t.category()) ? "MAJOR" : "GENERAL";
    }
}
