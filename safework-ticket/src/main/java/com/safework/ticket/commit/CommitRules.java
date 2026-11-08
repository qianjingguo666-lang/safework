package com.safework.ticket.commit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全风险研判与承诺公告规则（纯函数，有单元测试）。
 * <p>
 * 依据应急管理部《危险化学品企业安全风险研判与承诺公告制度》：每天生产、设备、工艺、安全负责人分别研判，
 * 主要负责人根据研判结果作出安全承诺并公告。这里要求 9:00 前完成研判、10:00 前承诺公告。
 */
public final class CommitRules {

    public static final String JUDGING = "JUDGING";
    public static final String COMMITTED = "COMMITTED";
    /** 没有任何研判 */
    public static final String NONE = "NONE";

    public static final LocalTime JUDGE_DEADLINE = LocalTime.of(9, 0);
    public static final LocalTime COMMIT_DEADLINE = LocalTime.of(10, 0);

    /** 研判项目 */
    public record Item(String code, String aspect, String text) {
    }

    public static final Map<String, String> ASPECTS = ordered("PRODUCTION", "生产负责人", "EQUIPMENT", "设备负责人",
            "PROCESS", "工艺负责人", "SAFETY", "安全负责人");

    public static final List<Item> ITEMS = List.of(
            new Item("P1", "PRODUCTION", "生产装置运行平稳，工艺参数在控制范围内"),
            new Item("P2", "PRODUCTION", "开停车、检修、试生产等非正常工况已制定方案并落实"),
            new Item("P3", "PRODUCTION", "当班人员配备齐全，关键岗位持证上岗"),
            new Item("P4", "PRODUCTION", "交接班记录完整，上一班的异常情况已处理"),
            new Item("E1", "EQUIPMENT", "关键设备、特种设备运行正常且在检验有效期内"),
            new Item("E2", "EQUIPMENT", "安全仪表系统、联锁正常投用，没有擅自摘除或旁路"),
            new Item("E3", "EQUIPMENT", "可燃、有毒气体检测报警器完好并投用"),
            new Item("E4", "EQUIPMENT", "消防设施、应急救援器材完好可用"),
            new Item("T1", "PROCESS", "工艺指标和操作规程执行到位，没有超温、超压、超液位"),
            new Item("T2", "PROCESS", "工艺、设备变更都履行了变更管理程序，临时变更按期恢复"),
            new Item("T3", "PROCESS", "储罐、仓库的危险化学品没有超过设计储量"),
            new Item("T4", "PROCESS", "重大危险源监测监控系统运行正常"),
            new Item("S1", "SAFETY", "今天的特殊作业都办理了作业票，安全措施落实到位"),
            new Item("S2", "SAFETY", "未整改的隐患（特别是重大隐患）已落实管控措施"),
            new Item("S3", "SAFETY", "承包商作业人员经过入厂安全培训"),
            new Item("S4", "SAFETY", "外来人员和危化品车辆按规定登记、核验"));

    /** 研判一项的结果 */
    public record Result(String code, boolean ok, String note, String measure) {
    }

    /** 生产装置运行情况 */
    public record Units(Integer total, Integer running, Integer stopped, Integer overhaul) {
    }

    private CommitRules() {
    }

    public static List<Item> itemsOf(String aspect) {
        return ITEMS.stream().filter(i -> i.aspect().equals(aspect)).toList();
    }

    /** 校验一个方面的研判：这个方面的项目一项不少、不能多；异常的要写情况和管控措施。不通过返回错误信息 */
    public static String checkJudgment(String aspect, List<Result> results) {
        if (!ASPECTS.containsKey(aspect)) {
            return "研判方面不正确";
        }
        List<Item> items = itemsOf(aspect);
        if (results == null || results.size() != items.size()) {
            return "请逐项研判（共 " + items.size() + " 项）";
        }
        Set<String> need = new HashSet<>(items.stream().map(Item::code).toList());
        for (Result r : results) {
            if (!need.remove(r.code())) {
                return "研判项目不正确";
            }
            if (!r.ok() && (isBlank(r.note()) || isBlank(r.measure()))) {
                Item it = items.stream().filter(i -> i.code().equals(r.code())).findFirst().orElseThrow();
                return "“" + it.text() + "”有异常，要写明情况和管控措施";
            }
        }
        return null;
    }

    /** 装置数：总数至少 1 套，运行 + 停产 + 检修 = 总数 */
    public static String checkUnits(Units u) {
        if (u == null || u.total() == null || u.running() == null || u.stopped() == null || u.overhaul() == null) {
            return "请填写生产装置运行情况";
        }
        if (u.total() < 1 || u.running() < 0 || u.stopped() < 0 || u.overhaul() < 0 || u.total() > 999) {
            return "装置数量不正确";
        }
        if (u.running() + u.stopped() + u.overhaul() != u.total()) {
            return "运行 " + u.running() + " + 停产 " + u.stopped() + " + 检修 " + u.overhaul() + " 套，和总数 " + u.total() + " 套对不上";
        }
        return null;
    }

    public static int abnormal(List<Result> results) {
        return (int) results.stream().filter(r -> !r.ok()).count();
    }

    /** 四个方面都研判了才能承诺 */
    public static List<String> missingAspects(Set<String> judged) {
        List<String> l = new ArrayList<>();
        ASPECTS.forEach((k, v) -> {
            if (!judged.contains(k)) {
                l.add(k);
            }
        });
        return l;
    }

    /** 承诺内容 */
    public static String commitmentText(int abnormal) {
        if (abnormal == 0) {
            return "今天我公司已进行安全风险研判，各项安全风险防控措施已落实到位，我承诺所有生产装置处于安全运行状态，罐区、仓库等重大危险源安全风险得到有效管控。";
        }
        return "今天我公司已进行安全风险研判，发现 " + abnormal + " 项异常情况，已逐项落实管控措施，我承诺在管控措施落实到位的前提下组织生产，罐区、仓库等重大危险源安全风险得到有效管控。";
    }

    /** 研判晚了：今天 9:00 以后还没研判完 */
    public static boolean judgeLate(LocalDate date, LocalDateTime now, boolean allJudged) {
        return !allJudged && isLate(date, now, JUDGE_DEADLINE);
    }

    /** 承诺晚了：今天 10:00 以后还没承诺 */
    public static boolean commitLate(LocalDate date, LocalDateTime now, boolean committed) {
        return !committed && isLate(date, now, COMMIT_DEADLINE);
    }

    /** 按时承诺率 = 按时承诺天数 ÷ 应承诺天数（百分数，一位小数）；应承诺 0 天时为 null */
    public static BigDecimal rate(int onTime, int days) {
        if (days <= 0) {
            return null;
        }
        return BigDecimal.valueOf(onTime * 100L).divide(BigDecimal.valueOf(days), 1, RoundingMode.HALF_UP);
    }

    /** 承诺时间在当天 10:00 前（含）才算按时 */
    public static boolean onTime(LocalDate date, LocalDateTime commitTime) {
        return commitTime != null && !commitTime.isAfter(date.atTime(COMMIT_DEADLINE));
    }

    private static boolean isLate(LocalDate date, LocalDateTime now, LocalTime deadline) {
        return now.isAfter(date.atTime(deadline));
    }

    private static boolean isBlank(String s) {
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
