package com.safework.safety.chem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 危险化学品的规则（纯计算，方便单元测试）。
 * <pre>
 * 重大危险源辨识（GB 18218-2018《危险化学品重大危险源辨识》）：
 *   S = Σ qᵢ / Qᵢ          qᵢ 单元内第 i 种危险化学品的最大存在量（t），Qᵢ 它的临界量（t）
 *   S ≥ 1 构成重大危险源
 * 分级：
 *   R = α · Σ βᵢ · qᵢ / Qᵢ  βᵢ 校正系数（毒性气体等更大），α 单元外 500 m 内暴露人员校正系数
 *   R ≥ 100 一级；50 ≤ R < 100 二级；10 ≤ R < 50 三级；R < 10 四级
 * </pre>
 * 临界量 Q 和校正系数 β 在危化品目录里维护（演示数据参考标准表 1、表 3，使用时以标准原文为准）。
 */
public final class ChemRules {

    public static final String IN = "IN";
    public static final String OUT = "OUT";
    /** 盘点：把库存改成实际数量 */
    public static final String CHECK = "CHECK";
    public static final Map<String, String> FLOW_TYPES = ordered(IN, "入库", OUT, "出库", CHECK, "盘点");

    /** 危险类别和默认校正系数 β（GB 18218 表 3，具体物质可以在目录里改） */
    public static final Map<String, String> CATEGORIES = ordered(
            "EXPLOSIVE", "爆炸物",
            "FLAMMABLE_GAS", "易燃气体",
            "FLAMMABLE_LIQUID", "易燃液体",
            "FLAMMABLE_SOLID", "易燃固体",
            "OXIDIZER", "氧化性物质",
            "TOXIC", "急性毒性",
            "CORROSIVE", "腐蚀性物质",
            "OTHER", "其他");
    private static final Map<String, BigDecimal> DEFAULT_BETA = Map.of(
            "EXPLOSIVE", new BigDecimal("2"),
            "FLAMMABLE_GAS", new BigDecimal("1.5"),
            "TOXIC", new BigDecimal("2"));

    public static final Map<String, String> STORE_TYPES = ordered(
            "TANK_AREA", "罐区", "WAREHOUSE", "库区（仓库）", "PRODUCTION", "生产装置");

    /** 重大危险源等级 */
    public static final Map<Integer, String> LEVELS = Map.of(1, "一级", 2, "二级", 3, "三级", 4, "四级");

    private static final int SCALE = 4;
    private static final BigDecimal MAX_QTY = new BigDecimal("1000000");

    private ChemRules() {
    }

    public static BigDecimal defaultBeta(String category) {
        return DEFAULT_BETA.getOrDefault(category, BigDecimal.ONE);
    }

    /** α：单元外 500 m 范围内常住人数（GB 18218 表 4） */
    public static BigDecimal alpha(int people) {
        if (people >= 100) {
            return new BigDecimal("2.0");
        }
        if (people >= 50) {
            return new BigDecimal("1.5");
        }
        if (people >= 30) {
            return new BigDecimal("1.2");
        }
        if (people >= 1) {
            return new BigDecimal("1.0");
        }
        return new BigDecimal("0.5");
    }

    /** R 值对应的等级 1 ~ 4 */
    public static int level(BigDecimal r) {
        if (r.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return 1;
        }
        if (r.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return 2;
        }
        if (r.compareTo(BigDecimal.TEN) >= 0) {
            return 3;
        }
        return 4;
    }

    /** 参与辨识的一种物质：q 最大存在量，Q 临界量（为空表示不在标准里，不参与计算），β 校正系数 */
    public record Part(String name, BigDecimal q, BigDecimal criticalQty, BigDecimal beta) {
    }

    /**
     * 辨识结果
     *
     * @param sum   S = Σ q/Q
     * @param major 是否构成重大危险源（S ≥ 1）
     * @param r     R 值（不构成时也算出来，方便看离一级还有多远）
     * @param level 等级 1 ~ 4；不构成重大危险源时为空
     * @param parts 每种物质的 q/Q，从大到小
     */
    public record Result(BigDecimal sum, boolean major, BigDecimal alpha, BigDecimal r, Integer level, List<Ratio> parts) {
    }

    public record Ratio(String name, BigDecimal q, BigDecimal criticalQty, BigDecimal beta, BigDecimal ratio) {
    }

    public static Result identify(List<Part> parts, int people) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        List<Ratio> ratios = new ArrayList<>();
        for (Part p : parts) {
            if (p.criticalQty() == null || p.criticalQty().signum() <= 0 || p.q() == null || p.q().signum() <= 0) {
                continue;
            }
            BigDecimal ratio = p.q().divide(p.criticalQty(), SCALE, RoundingMode.HALF_UP);
            BigDecimal beta = p.beta() == null ? BigDecimal.ONE : p.beta();
            sum = sum.add(ratio);
            weighted = weighted.add(beta.multiply(ratio));
            ratios.add(new Ratio(p.name(), p.q(), p.criticalQty(), beta, ratio));
        }
        ratios.sort((a, b) -> b.ratio().compareTo(a.ratio()));
        BigDecimal alpha = alpha(people);
        BigDecimal r = alpha.multiply(weighted).setScale(2, RoundingMode.HALF_UP);
        boolean major = sum.compareTo(BigDecimal.ONE) >= 0;
        return new Result(sum.setScale(2, RoundingMode.HALF_UP), major, alpha, r, major ? level(r) : null, ratios);
    }

    /**
     * 出入库校验，返回变动后的库存
     *
     * @param stock 当前库存
     * @param qty   入库、出库数量；盘点时是实际数量
     */
    public static BigDecimal apply(String type, BigDecimal stock, BigDecimal qty) {
        if (qty == null || qty.signum() < 0 || (!CHECK.equals(type) && qty.signum() == 0)) {
            throw new IllegalArgumentException(CHECK.equals(type) ? "盘点数量不能是负数" : "数量必须大于 0");
        }
        if (qty.compareTo(MAX_QTY) > 0) {
            throw new IllegalArgumentException("数量太大，请检查单位（吨）");
        }
        return switch (type) {
            case IN -> stock.add(qty);
            case OUT -> {
                if (qty.compareTo(stock) > 0) {
                    throw new IllegalArgumentException("库存只有 " + stock.stripTrailingZeros().toPlainString() + " 吨，不能出库 "
                            + qty.stripTrailingZeros().toPlainString() + " 吨");
                }
                yield stock.subtract(qty);
            }
            case CHECK -> qty;
            default -> throw new IllegalArgumentException("类型不正确");
        };
    }

    /** 超过设计最大储量 */
    public static boolean overstock(BigDecimal qty, BigDecimal maxQty) {
        return maxQty != null && qty != null && qty.compareTo(maxQty) > 0;
    }

    /** CAS 号格式：2~7 位-2 位-1 位校验位，校验位按标准算法核对 */
    public static boolean validCas(String cas) {
        if (cas == null || !cas.matches("\\d{2,7}-\\d{2}-\\d")) {
            return false;
        }
        String digits = cas.replace("-", "");
        int check = digits.charAt(digits.length() - 1) - '0';
        int sum = 0;
        for (int i = digits.length() - 2, w = 1; i >= 0; i--, w++) {
            sum += (digits.charAt(i) - '0') * w;
        }
        return sum % 10 == check;
    }

    private static <K> Map<K, String> ordered(Object... kv) {
        Map<K, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            @SuppressWarnings("unchecked") K k = (K) kv[i];
            m.put(k, (String) kv[i + 1]);
        }
        return m;
    }
}
