package com.safework.ai.core;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 作业票规则预审（确定性规则，不依赖大模型）。
 * 设计思路：能用规则判断的就不交给大模型——规则结果稳定、可解释、零成本；
 * 大模型只负责规则覆盖不到的部分（结合制度文件综合分析）。
 */
public final class TicketRuleChecker {

    public record Input(String ticketType, String safetyMeasures, Double gasOxygen, Double gasLel, Double gasH2s,
                        LocalDateTime gasCheckTime, LocalDateTime planStart, LocalDateTime planEnd,
                        LocalDateTime now) {
    }

    /** level：HIGH 必须处理 / MEDIUM 建议补充 / LOW 提示 */
    public record Finding(String level, String item, String problem, String suggestion) {
    }

    /** 每种作业的安全措施应覆盖的要点：要点名称 → 任意一个关键词出现即视为覆盖 */
    private static final Map<String, List<String[]>> REQUIRED = Map.of(
            "CONFINED_SPACE", List.of(
                    new String[]{"通风", "通风"},
                    new String[]{"隔离（盲板、切断、断电）", "隔离", "盲板", "切断", "断电", "关闭阀门"},
                    new String[]{"气体检测", "检测", "监测", "分析"},
                    new String[]{"个体防护（呼吸器、防毒面具）", "呼吸器", "防毒面具", "面罩", "防护"},
                    new String[]{"应急救援（三脚架、安全绳）", "救援", "应急", "三脚架", "安全绳"}),
            "HOT_WORK", List.of(
                    new String[]{"消防器材", "灭火器", "消防", "灭火"},
                    new String[]{"清理可燃物", "清理", "可燃物", "易燃物"},
                    new String[]{"气体检测", "检测", "分析"},
                    new String[]{"动火监护", "监护", "监火"}),
            "HEIGHT", List.of(
                    new String[]{"安全带", "安全带", "安全绳"},
                    new String[]{"防护设施（护栏、安全网、脚手架）", "护栏", "安全网", "脚手架", "防护栏"},
                    new String[]{"防坠物（工具袋）", "工具袋", "防坠", "坠物"}));

    private TicketRuleChecker() {
    }

    public static List<Finding> check(Input in) {
        List<Finding> list = new ArrayList<>();
        String measures = in.safetyMeasures() == null ? "" : in.safetyMeasures();
        boolean gasRequired = "CONFINED_SPACE".equals(in.ticketType()) || "HOT_WORK".equals(in.ticketType());

        if (measures.isBlank()) {
            list.add(new Finding("HIGH", "安全措施", "未填写安全措施", "逐条填写本次作业落实的安全措施"));
        } else {
            for (String[] group : REQUIRED.getOrDefault(in.ticketType(), List.of())) {
                boolean covered = false;
                for (int i = 1; i < group.length; i++) {
                    if (measures.contains(group[i])) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    list.add(new Finding("MEDIUM", "安全措施", "安全措施中没有提到：" + group[0], "补充“" + group[0] + "”相关措施"));
                }
            }
        }

        if (gasRequired) {
            if (in.gasOxygen() == null || in.gasLel() == null) {
                list.add(new Finding("HIGH", "气体检测", "缺少气体检测数据", "作业前进行气体检测并录入结果"));
            } else {
                double o2 = in.gasOxygen();
                if (o2 < 19.5 || o2 > 23.5) {
                    list.add(new Finding("HIGH", "气体检测", "氧含量 " + o2 + "% 不合格", "加强通风后重新检测"));
                } else if (o2 < 20.0 || o2 > 23.0) {
                    list.add(new Finding("MEDIUM", "气体检测", "氧含量 " + o2 + "% 接近限值", "作业中持续监测氧含量"));
                }
                if (in.gasLel() > 10) {
                    list.add(new Finding("HIGH", "气体检测", "可燃气体 " + in.gasLel() + "%LEL 超标", "查明泄漏源，置换合格后再作业"));
                } else if (in.gasLel() > 5) {
                    list.add(new Finding("MEDIUM", "气体检测", "可燃气体 " + in.gasLel() + "%LEL 偏高", "作业过程中连续监测"));
                }
                if (in.gasH2s() != null && in.gasH2s() > 10) {
                    list.add(new Finding("HIGH", "气体检测", "硫化氢 " + in.gasH2s() + "mg/m³ 超标", "通风置换，佩戴正压式呼吸器"));
                }
            }
            if (in.gasCheckTime() != null && in.now() != null
                    && Duration.between(in.gasCheckTime(), in.now()).toMinutes() > 30) {
                list.add(new Finding("MEDIUM", "气体检测", "气体检测距今已超过 30 分钟", "开工前由监护人现场复测"));
            }
        }

        if (in.planStart() != null && in.planEnd() != null) {
            long hours = Duration.between(in.planStart(), in.planEnd()).toHours();
            if (hours > 8) {
                list.add(new Finding("LOW", "作业时间", "计划作业时长 " + hours + " 小时", "超过一个班次，建议分段办理作业票"));
            }
        }
        return list;
    }
}
