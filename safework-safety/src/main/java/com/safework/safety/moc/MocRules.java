package com.safework.safety.moc;

import com.safework.safety.enums.RiskLevel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 变更管理（MOC，Management of Change）的规则（纯计算，方便单元测试）。
 * <pre>
 * 草稿 ──提交──▶ 风险评估 ──评估（LEC + 措施）──▶ 待审批 ──审批通过（一般 1 人；较大、重大风险 2 人）──▶ 实施中
 *  ▲               │ 退回                         │ 驳回（重新评估）                               │ 实施完成
 *  └───────────────┘◀─────────────────────────────┘                                              ▼
 *                                                     已投用（关闭）◀──投用前安全检查（PSSR）全部合格── 待投用检查
 *                                                          │ 临时变更：到期恢复原状        不合格 → 退回实施中
 * </pre>
 */
public final class MocRules {

    public static final String DRAFT = "DRAFT";
    public static final String ASSESSING = "ASSESSING";
    public static final String APPROVING = "APPROVING";
    public static final String IMPLEMENTING = "IMPLEMENTING";
    public static final String PSSR = "PSSR";
    public static final String CLOSED = "CLOSED";
    public static final String CANCELLED = "CANCELLED";

    public static final Map<String, String> STATUSES = ordered(
            DRAFT, "草稿", ASSESSING, "风险评估", APPROVING, "待审批", IMPLEMENTING, "实施中",
            PSSR, "投用前检查", CLOSED, "已投用", CANCELLED, "已取消");

    public static final Map<String, String> TYPES = ordered(
            "PROCESS", "工艺变更", "EQUIPMENT", "设备设施变更", "MATERIAL", "原辅料变更",
            "PERSONNEL", "关键岗位人员变更", "MANAGEMENT", "管理制度变更");

    /** 投用前安全检查（PSSR）项目 */
    public static final List<String> PSSR_ITEMS = List.of(
            "变更按批准的方案实施，与设计一致",
            "风险评估提出的控制措施已全部落实",
            "操作规程、工艺卡片已修订",
            "相关人员已完成培训并考核合格",
            "图纸、P&ID、设备台账已更新",
            "安全设施、报警联锁已投用并测试正常",
            "应急预案、处置卡已更新（不涉及的写明“不涉及”）");

    public static final int MAX_TEMPORARY_DAYS = 180;

    private MocRules() {
    }

    /** 状态动作：from 可以执行的状态，to 执行后的状态（审批的最终状态由审批人数决定，这里写 APPROVING） */
    public enum Action {
        SUBMIT(Set.of(DRAFT), ASSESSING, "提交"),
        RETURN(Set.of(ASSESSING), DRAFT, "退回"),
        ASSESS(Set.of(ASSESSING), APPROVING, "风险评估"),
        APPROVE(Set.of(APPROVING), APPROVING, "审批通过"),
        REJECT(Set.of(APPROVING), ASSESSING, "驳回"),
        IMPLEMENTED(Set.of(IMPLEMENTING), PSSR, "实施完成"),
        PSSR_PASS(Set.of(PSSR), CLOSED, "投用前检查合格"),
        PSSR_FAIL(Set.of(PSSR), IMPLEMENTING, "投用前检查不合格"),
        CANCEL(Set.of(DRAFT, ASSESSING, APPROVING), CANCELLED, "取消"),
        RESTORE(Set.of(CLOSED), CLOSED, "临时变更已恢复");

        public final Set<String> from;
        public final String to;
        public final String label;

        Action(Set<String> from, String to, String label) {
            this.from = from;
            this.to = to;
            this.label = label;
        }

        public boolean canFire(String status) {
            return from.contains(status);
        }
    }

    /** 需要几个人审批：较大、重大风险两级（部门 + 公司），一般和低风险一级 */
    public static int approvalsNeeded(RiskLevel level) {
        return level == RiskLevel.MAJOR || level == RiskLevel.HIGH ? 2 : 1;
    }

    /** 临时变更必须写恢复日期，最长 180 天；永久变更不能写 */
    public static String checkTemporary(boolean temporary, LocalDate restoreDate, LocalDate today) {
        if (!temporary) {
            return restoreDate == null ? null : "永久变更不需要恢复日期";
        }
        if (restoreDate == null) {
            return "临时变更请填写恢复日期";
        }
        if (!restoreDate.isAfter(today)) {
            return "恢复日期要晚于今天";
        }
        if (restoreDate.isAfter(today.plusDays(MAX_TEMPORARY_DAYS))) {
            return "临时变更最长 " + MAX_TEMPORARY_DAYS + " 天，更长的请按永久变更办理";
        }
        return null;
    }

    /**
     * PSSR 结果校验：每一项都要勾“合格”或“不合格”，不合格必须写原因。
     *
     * @return null 表示格式正确
     */
    public static String checkPssr(List<Boolean> results, List<String> notes) {
        if (results == null || results.size() != PSSR_ITEMS.size()) {
            return "请逐项填写投用前安全检查（共 " + PSSR_ITEMS.size() + " 项）";
        }
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i) == null) {
                return "第 " + (i + 1) + " 项还没检查";
            }
            if (!results.get(i)) {
                String n = notes == null || notes.size() <= i ? null : notes.get(i);
                if (n == null || n.isBlank()) {
                    return "第 " + (i + 1) + " 项不合格，请写明原因";
                }
            }
        }
        return null;
    }

    public static boolean pssrPassed(List<Boolean> results) {
        return results.stream().allMatch(Boolean.TRUE::equals);
    }

    /** 临时变更恢复提醒：到期前 3 天、当天；过期后第 1 天起每 7 天 */
    public static boolean shouldRemindRestore(LocalDate restoreDate, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, restoreDate);
        if (days == 3 || days == 0) {
            return true;
        }
        return days < 0 && (-days - 1) % 7 == 0;
    }

    private static Map<String, String> ordered(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
