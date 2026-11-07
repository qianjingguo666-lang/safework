package com.safework.ticket.std;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全生产标准化自评的规则（纯计算，方便单元测试）。
 * <pre>
 * 考评项目：按《企业安全生产标准化基本规范》（GB/T 33000-2016）的 8 个一级要素组织，27 项，满分 100
 * 每项：标准分值、考评办法（扣分规则）；auto = 系统能从各模块数据给出“建议扣分”和证据，评审人确认或调整
 * 得分（百分制）= （标准分合计 - 扣分合计 - 空项分值）/（标准分合计 - 空项分值）× 100
 *   空项：企业没有这项业务（例如没有危险化学品），不参与计分
 * 等级（参考常见的行业定级标准，以所在地区的行业标准为准）：
 *   一级 ≥ 90 分，且近一年没有重伤及以上事故；二级 ≥ 75 分，且近一年没有死亡事故；三级 ≥ 60 分，且近一年死亡不超过 1 人
 *   同时满足评分和安全绩效，取低的等级
 * </pre>
 */
public final class StdRules {

    public record Element(int no, String name) {
    }

    /**
     * 考评项目
     *
     * @param auto 系统能自动取证、给出建议扣分
     */
    public record Item(String code, int element, String title, String content, int score, String method, boolean auto) {
    }

    public static final List<Element> ELEMENTS = List.of(
            new Element(1, "目标职责"),
            new Element(2, "制度化管理"),
            new Element(3, "教育培训"),
            new Element(4, "现场管理"),
            new Element(5, "安全风险管控及隐患排查治理"),
            new Element(6, "应急管理"),
            new Element(7, "事故管理"),
            new Element(8, "持续改进"));

    public static final List<Item> ITEMS = List.of(
            new Item("1.1", 1, "安全生产目标", "制定年度安全生产目标和指标，分解到各部门，定期监测和考核完成情况", 4, "无年度目标不得分；未定期考核扣 1 分；综合得分低于 75 分扣 1 分", true),
            new Item("1.2", 1, "机构和职责", "设置安全生产管理机构，配备专职安全生产管理人员；主要负责人履行第一责任人职责", 2, "未设置机构或未配备专职人员不得分", true),
            new Item("1.3", 1, "全员参与", "建立全员安全生产责任制，逐级签订安全生产责任书，并对落实情况进行考核", 4, "无责任书不得分；每缺 1 人签署扣 0.5 分，最多扣 3 分", true),
            new Item("1.4", 1, "安全生产投入", "按规定足额提取安全生产费用，编制使用计划，建立使用台账，专款专用", 4, "无年度计划扣 2 分；已用超过可用额度扣 1 分；每有 1 类超预算扣 0.5 分，最多 1 分", true),
            new Item("1.5", 1, "安全文化建设", "开展安全文化建设，鼓励从业人员报告事故隐患并给予奖励", 2, "未开展不得分；无隐患报告奖励扣 1 分", false),
            new Item("2.1", 2, "法规标准识别", "建立安全生产法律法规、标准规范清单，及时识别、获取和更新", 3, "无清单不得分；未及时更新扣 1 分", false),
            new Item("2.2", 2, "规章制度", "建立健全安全生产规章制度，以文件形式发布并发放到相关岗位", 4, "缺少必要制度每项扣 1 分；未正式发布扣 2 分", false),
            new Item("2.3", 2, "操作规程", "编制岗位安全操作规程，发放到岗位并组织培训", 3, "缺少岗位操作规程每个岗位扣 1 分", false),
            new Item("3.1", 3, "教育培训管理", "制定年度培训计划，组织安全培训和考试，建立培训档案", 5, "全年没有考试不得分；考试少于 2 次扣 1 分；合格率低于 90% 扣 1 分", true),
            new Item("3.2", 3, "人员资质", "特种作业人员、安全管理人员持证上岗，证书在有效期内", 5, "每有 1 本证书过期扣 1 分", true),
            new Item("4.1", 4, "设备设施管理", "建立设备设施台账，特种设备、安全附件、检测报警仪器按期检验", 6, "每有 1 台在用设备逾期未检或从未检验扣 1 分", true),
            new Item("4.2", 4, "作业安全", "动火、受限空间等特殊作业实行作业票审批，现场安全措施落实到位", 6, "全年没有作业票记录不得分；超过计划时间仍未结束的作业每张扣 1 分", true),
            new Item("4.3", 4, "危险化学品管理", "建立危险化学品台账，安全技术说明书（MSDS）齐全，储存量不超过设计最大储量", 4, "每缺 1 份 MSDS 扣 0.5 分，最多 2 分；有超储扣 2 分", true),
            new Item("4.4", 4, "相关方管理", "对承包商进行资质审查、入厂安全培训和作业过程监督", 3, "资质或保险快到期 / 过期、人员培训过期，每类扣 1 分", true),
            new Item("4.5", 4, "职业健康", "接触职业病危害的人员按期体检，职业禁忌证人员调离接害岗位", 3, "有职业禁忌证人员仍在接害岗位扣 2 分；体检逾期或未体检扣 1 分", true),
            new Item("4.6", 4, "劳动防护用品", "按标准配备劳动防护用品，到期及时更换", 2, "有过期未更换扣 1 分；库存不足扣 1 分", true),
            new Item("5.1", 5, "安全风险管理", "开展安全风险辨识评估，确定风险等级，落实分级管控和定期巡检", 6, "未辨识风险点不得分；每个风险点巡检逾期扣 1 分，最多 4 分", true),
            new Item("5.2", 5, "重大危险源辨识与管理", "按 GB 18218 辨识重大危险源，确定等级并落实监控措施", 3, "未开展辨识不得分", true),
            new Item("5.3", 5, "隐患排查治理", "定期开展隐患排查，隐患按期整改、验收闭环", 7, "逾期未整改的隐患每条扣 1 分，最多 4 分；按期整改率低于 90% 扣 1 分；有安全检查逾期未完成扣 1 分", true),
            new Item("5.4", 5, "变更管理", "工艺、设备、人员变更实行风险评估和审批，临时变更按期恢复", 4, "每项临时变更超期未恢复扣 2 分", true),
            new Item("6.1", 6, "应急准备", "编制应急预案，按规定周期组织演练并评估", 5, "没有应急预案不得分；每个预案未按期演练扣 1 分，最多 3 分", true),
            new Item("6.2", 6, "应急设施和物资", "配备消防和应急救援设施、物资，定期检查维护", 2, "每台消防或应急设施逾期未检扣 1 分", true),
            new Item("6.3", 6, "应急处置与评估", "发生事件后按预案及时响应处置，结束后进行总结评估", 3, "未按预案处置或未总结评估的每起扣 1 分", true),
            new Item("7.1", 7, "事故报告", "发生事故按规定及时报告，不迟报、漏报、谎报、瞒报", 2, "有迟报、漏报不得分", true),
            new Item("7.2", 7, "事故调查和处理", "按“四不放过”原则调查处理事故，落实整改措施", 4, "每起调查超期未结案扣 2 分；每项整改措施逾期扣 1 分", true),
            new Item("8.1", 8, "绩效评定", "每年至少开展一次安全生产标准化自评，评价体系运行效果", 2, "一年内没有完成自评不得分", true),
            new Item("8.2", 8, "持续改进", "根据自评和安全目标完成情况制定改进措施并落实", 2, "上次自评扣分项转的整改隐患未闭环扣 1 分；安全目标有红灯指标扣 1 分", true));

    public static final Map<String, Item> BY_CODE = new LinkedHashMap<>();

    static {
        ITEMS.forEach(i -> BY_CODE.put(i.code(), i));
    }

    public static final String DRAFT = "DRAFT";
    public static final String DONE = "DONE";

    public static final String GRADE_1 = "一级";
    public static final String GRADE_2 = "二级";
    public static final String GRADE_3 = "三级";
    public static final String GRADE_NONE = "未达标";

    /** 一项的评分情况 */
    public record Scored(String code, int element, int score, boolean na, double deduct) {
    }

    /** 一个要素的小计 */
    public record ElementScore(int no, String name, int full, int na, double deduct, Double rate) {
    }

    private StdRules() {
    }

    /** 扣分校验：0 ~ 标准分，按 0.5 分扣 */
    public static String checkDeduct(int score, double deduct) {
        if (deduct < 0 || deduct > score) {
            return "扣分要在 0 ~ " + score + " 分之间";
        }
        if (Math.abs(deduct * 2 - Math.round(deduct * 2)) > 1e-9) {
            return "扣分按 0.5 分为单位";
        }
        return null;
    }

    /** 百分制得分，保留一位小数；全部是空项返回 null */
    public static Double total(List<Scored> items) {
        double full = 0;
        double got = 0;
        for (Scored s : items) {
            if (!s.na()) {
                full += s.score();
                got += s.score() - Math.min(s.score(), s.deduct());
            }
        }
        return full <= 0 ? null : Math.round(got * 1000.0 / full) / 10.0;
    }

    /** 每个要素的小计：满分、空项分值、扣分、得分率（%） */
    public static List<ElementScore> elements(List<Scored> items) {
        List<ElementScore> list = new ArrayList<>();
        for (Element e : ELEMENTS) {
            int full = 0;
            int na = 0;
            double deduct = 0;
            for (Scored s : items) {
                if (s.element() != e.no()) {
                    continue;
                }
                full += s.score();
                if (s.na()) {
                    na += s.score();
                } else {
                    deduct += Math.min(s.score(), s.deduct());
                }
            }
            int valid = full - na;
            list.add(new ElementScore(e.no(), e.name(), full, na, deduct, valid <= 0 ? null : Math.round((valid - deduct) * 1000.0 / valid) / 10.0));
        }
        return list;
    }

    /** 等级：评分和安全绩效（近一年死亡、重伤人数）同时满足，取低的 */
    public static String grade(Double score, int dead, int serious) {
        if (score == null) {
            return GRADE_NONE;
        }
        int byScore = score >= 90 ? 1 : score >= 75 ? 2 : score >= 60 ? 3 : 4;
        int byPerformance = dead + serious == 0 ? 1 : dead == 0 ? 2 : dead <= 1 ? 3 : 4;
        return switch (Math.max(byScore, byPerformance)) {
            case 1 -> GRADE_1;
            case 2 -> GRADE_2;
            case 3 -> GRADE_3;
            default -> GRADE_NONE;
        };
    }

    /** 按个数扣分：每个扣 each 分，最多扣 cap 分（不超过标准分） */
    public static double perCount(long count, double each, double cap) {
        return Math.min(cap, Math.max(0, count) * each);
    }

    /** 建议扣分合计，不超过标准分，按 0.5 取整 */
    public static double clamp(double deduct, int score) {
        double d = Math.max(0, Math.min(score, deduct));
        return Math.round(d * 2) / 2.0;
    }
}
