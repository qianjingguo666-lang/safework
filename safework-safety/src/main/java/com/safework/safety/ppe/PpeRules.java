package com.safework.safety.ppe;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 劳保用品规则（纯函数，方便单元测试）：
 * <ul>
 *   <li>每种用品有使用期限（月），发放日期 + 期限 = 到期日期</li>
 *   <li>岗位定发放标准；每个人按所在岗位“应有”的用品和“实发”的记录对比：未发放 / 已过期 / 30 天内到期 / 正常</li>
 *   <li>批量补发先算总需求，库存不够就一件都不发（不能发一半）</li>
 * </ul>
 */
public final class PpeRules {

    /** 防护部位分类 */
    public static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("HEAD", "头部防护");
        CATEGORIES.put("RESPIRATORY", "呼吸防护");
        CATEGORIES.put("EYE_FACE", "眼面防护");
        CATEGORIES.put("HEARING", "听力防护");
        CATEGORIES.put("HAND", "手部防护");
        CATEGORIES.put("FOOT", "足部防护");
        CATEGORIES.put("BODY", "躯体防护");
        CATEGORIES.put("FALL", "坠落防护");
    }

    public static final String MISSING = "MISSING";
    public static final String EXPIRED = "EXPIRED";
    public static final String DUE_SOON = "DUE_SOON";
    public static final String OK = "OK";

    /** 发放记录状态：在用 / 已更换（重新发放后旧的自动变成已更换） */
    public static final String IN_USE = "IN_USE";
    public static final String REPLACED = "REPLACED";

    public static final int WARN_DAYS = 30;
    public static final int MAX_MONTHS = 120;
    public static final int MAX_QUANTITY = 20;

    private PpeRules() {
    }

    public static LocalDate expire(LocalDate issueDate, int validMonths) {
        return issueDate.plusMonths(validMonths);
    }

    /** 到期状态；从没发过（expire 为空）是“未发放” */
    public static String state(LocalDate expire, LocalDate today) {
        if (expire == null) {
            return MISSING;
        }
        if (expire.isBefore(today)) {
            return EXPIRED;
        }
        return expire.isAfter(today.plusDays(WARN_DAYS)) ? OK : DUE_SOON;
    }

    /** 需要发放：未发放或已过期 */
    public static boolean needIssue(String state) {
        return MISSING.equals(state) || EXPIRED.equals(state);
    }

    /** 提醒本人：到期前 7 天、到期当天各一次；过期后第 1 天起每 7 天一次 */
    public static boolean shouldRemind(LocalDate expire, LocalDate today) {
        if (expire == null) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(today, expire);
        if (days == 7 || days == 0) {
            return true;
        }
        return days < 0 && (-days - 1) % 7 == 0;
    }

    /** 使用期限校验：1 ~ 120 个月 */
    public static String checkMonths(Integer months) {
        if (months == null || months < 1 || months > MAX_MONTHS) {
            return "使用期限要在 1 ~ " + MAX_MONTHS + " 个月之间";
        }
        return null;
    }

    /**
     * 库存够不够：need = 每种用品要发的数量，stock = 现有库存，names = 用品名称。
     *
     * @return 不够的用品说明，如“滤毒罐 需要 6，库存 3”；都够返回空列表
     */
    public static List<String> shortage(Map<Long, Integer> need, Map<Long, Integer> stock, Map<Long, String> names) {
        List<String> list = new ArrayList<>();
        need.forEach((id, n) -> {
            int s = stock.getOrDefault(id, 0);
            if (n > s) {
                list.add(names.getOrDefault(id, "用品" + id) + " 需要 " + n + "，库存 " + s);
            }
        });
        return list;
    }
}
