package com.safework.safety.service;

import com.safework.safety.enums.RiskLevel;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 作业条件危险性评价法（LEC）：D = L × E × C
 * <ul>
 *   <li>L：发生事故的可能性</li>
 *   <li>E：人员暴露于危险环境的频繁程度</li>
 *   <li>C：一旦发生事故可能造成的后果</li>
 * </ul>
 * 分级：D &gt; 320 重大风险（红），160 &lt; D ≤ 320 较大风险（橙），70 &lt; D ≤ 160 一般风险（黄），D ≤ 70 低风险（蓝）。
 * <p>
 * L、E、C 只能取标准表里的分值，不能随便填：随便填的话 D 值没有可比性，同一个风险不同人评出来的等级会不一样。
 */
public final class LecEvaluator {

    public record Option(double value, String label) {
    }

    public record Result(double d, RiskLevel level) {
    }

    public static final List<Option> L = List.of(
            new Option(10, "完全可以预料"), new Option(6, "相当可能"), new Option(3, "可能，但不经常"),
            new Option(1, "可能性小，完全意外"), new Option(0.5, "很不可能，可以设想"), new Option(0.2, "极不可能"),
            new Option(0.1, "实际不可能"));
    public static final List<Option> E = List.of(
            new Option(10, "连续暴露"), new Option(6, "每天工作时间内暴露"), new Option(3, "每周一次或偶然暴露"),
            new Option(2, "每月一次暴露"), new Option(1, "每年几次暴露"), new Option(0.5, "非常罕见地暴露"));
    public static final List<Option> C = List.of(
            new Option(100, "大灾难，许多人死亡"), new Option(40, "灾难，数人死亡"), new Option(15, "非常严重，一人死亡"),
            new Option(7, "严重，重伤"), new Option(3, "重大，致残"), new Option(1, "引人注目，需要救护"));

    private LecEvaluator() {
    }

    public static Result evaluate(Double l, Double e, Double c) {
        check("L（可能性）", l, L);
        check("E（暴露频繁程度）", e, E);
        check("C（后果）", c, C);
        double d = Math.round(l * e * c * 10) / 10.0;
        return new Result(d, levelOf(d));
    }

    public static RiskLevel levelOf(double d) {
        if (d > 320) {
            return RiskLevel.MAJOR;
        }
        if (d > 160) {
            return RiskLevel.HIGH;
        }
        if (d > 70) {
            return RiskLevel.GENERAL;
        }
        return RiskLevel.LOW;
    }

    private static void check(String name, Double v, List<Option> options) {
        if (v == null || options.stream().noneMatch(o -> Math.abs(o.value() - v) < 1e-9)) {
            throw new IllegalArgumentException(name + " 只能取标准分值："
                    + options.stream().map(o -> fmt(o.value())).collect(Collectors.joining("、")));
        }
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
