package com.safework.ticket.resp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全生产责任制规则（纯函数，方便单元测试）：
 * <ul>
 *   <li>岗位安全职责：每个岗位一份职责清单；一个人在多个岗位时，责任书里合并去重</li>
 *   <li>年度责任书：草稿 → 发布（给岗位上的每个人生成一份待签署的责任书）→ 结束签署；发布后正文和职责不能再改</li>
 *   <li>签署：本人阅读后手写签名；截止日期之后签的记为“逾期签署”</li>
 *   <li>年度考核：0 ~ 100 分，90 优秀、80 良好、60 合格，低于 60 不合格</li>
 * </ul>
 */
public final class RespRules {

    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String CLOSED = "CLOSED";
    public static final Map<String, String> BOOK_STATUS = Map.of(DRAFT, "草稿", PUBLISHED, "签署中", CLOSED, "已结束");

    public static final String PENDING = "PENDING";
    public static final String SIGNED = "SIGNED";

    public static final int MAX_DUTIES = 30;
    public static final int MAX_DUTY_LENGTH = 200;

    private RespRules() {
    }

    /** 职责清单校验：1 ~ 30 条，每条不超过 200 字，不能重复 */
    public static String checkDuties(List<String> duties) {
        List<String> list = clean(duties);
        if (list.isEmpty()) {
            return "至少写一条安全职责";
        }
        if (list.size() > MAX_DUTIES) {
            return "安全职责最多 " + MAX_DUTIES + " 条";
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).length() > MAX_DUTY_LENGTH) {
                return "第 " + (i + 1) + " 条超过 " + MAX_DUTY_LENGTH + " 字";
            }
        }
        if (new LinkedHashSet<>(list).size() < list.size()) {
            return "有重复的职责";
        }
        return null;
    }

    /** 去掉空行、首尾空格、前面手打的编号（如“1.”“2、”） */
    public static List<String> clean(List<String> duties) {
        List<String> list = new ArrayList<>();
        if (duties == null) {
            return list;
        }
        for (String d : duties) {
            if (d == null) {
                continue;
            }
            String s = d.trim().replaceFirst("^\\d{1,2}\\s*[.、．)）]\\s*", "").trim();
            if (!s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    /** 一个人在多个岗位：职责合并，按岗位顺序去重 */
    public static List<String> merge(List<List<String>> postDuties) {
        Set<String> set = new LinkedHashSet<>();
        postDuties.forEach(l -> set.addAll(clean(l)));
        return new ArrayList<>(set);
    }

    /** 年度：只能是去年、今年、明年 */
    public static String checkYear(int year, LocalDate today) {
        if (year < today.getYear() - 1 || year > today.getYear() + 1) {
            return "年度只能是 " + (today.getYear() - 1) + " ~ " + (today.getYear() + 1);
        }
        return null;
    }

    /** 考核等级 */
    public static String grade(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("考核分数要在 0 ~ 100 之间");
        }
        if (score >= 90) {
            return "优秀";
        }
        if (score >= 80) {
            return "良好";
        }
        return score >= 60 ? "合格" : "不合格";
    }

    /** 截止日期当天签的不算逾期 */
    public static boolean late(LocalDateTime signTime, LocalDate deadline) {
        return signTime != null && deadline != null && signTime.toLocalDate().isAfter(deadline);
    }

    /** 未签署提醒：截止前 3 天、前 1 天、当天；逾期后每 3 天一次 */
    public static boolean shouldRemind(LocalDate deadline, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, deadline);
        if (days == 3 || days == 1 || days == 0) {
            return true;
        }
        return days < 0 && (-days - 1) % 3 == 0;
    }

    /** 百分比，分母为 0 时为空（前端显示“-”） */
    public static Integer percent(long part, long total) {
        return total == 0 ? null : (int) Math.round(part * 100.0 / total);
    }
}
