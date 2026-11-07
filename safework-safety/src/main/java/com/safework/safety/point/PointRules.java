package com.safework.safety.point;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 隐患随手拍积分的规则（纯计算，方便单元测试）。
 * <pre>
 * 得分：上报的隐患被安全员确认（指派整改）后，上报人得分：一般隐患 10 分、重大隐患 50 分（撤销的不得分）
 *       整改责任人按期完成整改（验收通过时整改时间不晚于期限）得 5 分
 *       管理员可以手工加减分（合理化建议、违章扣分等），必须写原因，每次不超过 200 分，余额不能扣成负数
 * 同一条隐患、同一个人、同一种原因只得一次分（重复事件不会重复加分）
 * 兑换：积分够、奖品有库存才能兑换，先扣积分；管理员发放后奖品的金额记入安全投入（“隐患报告奖励”属于安全教育培训类）；
 *       管理员退回时积分和库存都还回去
 * 排行榜：按统计期内“得到”的积分排（兑换花掉的不影响排名），同分同名次
 * </pre>
 */
public final class PointRules {

    public static final int REPORT_GENERAL = 10;
    public static final int REPORT_MAJOR = 50;
    public static final int RECTIFY_ON_TIME = 5;
    public static final int MAX_ADJUST = 200;

    public static final String HAZARD_REPORT = "HAZARD_REPORT";
    public static final String HAZARD_RECTIFY = "HAZARD_RECTIFY";
    public static final String MANUAL = "MANUAL";
    public static final String REDEEM = "REDEEM";
    public static final String REDEEM_RETURN = "REDEEM_RETURN";

    public static final Map<String, String> SOURCE_TYPES = new LinkedHashMap<>();

    static {
        SOURCE_TYPES.put(HAZARD_REPORT, "上报隐患");
        SOURCE_TYPES.put(HAZARD_RECTIFY, "按期整改");
        SOURCE_TYPES.put(MANUAL, "手工调整");
        SOURCE_TYPES.put(REDEEM, "兑换奖品");
        SOURCE_TYPES.put(REDEEM_RETURN, "兑换退回");
    }

    public static final String PENDING = "PENDING";
    public static final String ISSUED = "ISSUED";
    public static final String REJECTED = "REJECTED";

    public static final String MONTH = "MONTH";
    public static final String YEAR = "YEAR";

    /** 排行榜一行 */
    public record Ranked(Long userId, int points, int rank) {
    }

    private PointRules() {
    }

    /** 上报的隐患被确认后上报人得多少分 */
    public static int reportPoints(String level) {
        return "MAJOR".equals(level) ? REPORT_MAJOR : REPORT_GENERAL;
    }

    /** 整改按期完成得分：整改时间不晚于期限 */
    public static int rectifyPoints(LocalDateTime rectifyTime, LocalDateTime deadline) {
        if (rectifyTime == null || deadline == null) {
            return 0;
        }
        return rectifyTime.isAfter(deadline) ? 0 : RECTIFY_ON_TIME;
    }

    /** 手工调整校验 */
    public static String checkAdjust(int change, String reason, int balance) {
        if (change == 0) {
            return "调整的积分不能是 0";
        }
        if (Math.abs(change) > MAX_ADJUST) {
            return "每次调整不超过 " + MAX_ADJUST + " 分";
        }
        if (reason == null || reason.isBlank()) {
            return "请填写调整原因";
        }
        if (balance + change < 0) {
            return "积分余额只有 " + balance + " 分，不能扣成负数";
        }
        return null;
    }

    /** 兑换校验 */
    public static String checkRedeem(int balance, int cost, int stock, boolean enabled) {
        if (!enabled) {
            return "这个奖品已下架";
        }
        if (stock <= 0) {
            return "奖品已经兑完了";
        }
        if (balance < cost) {
            return "积分不够：需要 " + cost + " 分，你有 " + balance + " 分";
        }
        return null;
    }

    /** 统计区间 [from, to)：本月或本年 */
    public static LocalDate[] range(String period, int year, int month) {
        if (YEAR.equals(period)) {
            LocalDate from = LocalDate.of(year, 1, 1);
            return new LocalDate[]{from, from.plusYears(1)};
        }
        LocalDate from = LocalDate.of(year, month, 1);
        return new LocalDate[]{from, from.plusMonths(1)};
    }

    /** 排名：按得分从高到低，同分同名次（1、2、2、4） */
    public static List<Ranked> rank(Map<Long, Integer> points) {
        List<Map.Entry<Long, Integer>> list = new ArrayList<>(points.entrySet());
        list.removeIf(e -> e.getValue() == null || e.getValue() <= 0);
        list.sort((a, b) -> b.getValue().equals(a.getValue()) ? Long.compare(a.getKey(), b.getKey()) : Integer.compare(b.getValue(), a.getValue()));
        List<Ranked> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            int rank = i > 0 && list.get(i).getValue().equals(list.get(i - 1).getValue()) ? result.get(i - 1).rank() : i + 1;
            result.add(new Ranked(list.get(i).getKey(), list.get(i).getValue(), rank));
        }
        return result;
    }
}
