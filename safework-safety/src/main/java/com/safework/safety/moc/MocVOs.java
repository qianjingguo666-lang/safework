package com.safework.safety.moc;

import com.safework.safety.entity.MocChange;
import com.safework.safety.entity.MocLog;

import java.util.List;

/** 变更管理的返回对象 */
public final class MocVOs {

    private MocVOs() {
    }

    public record PssrItem(String item, Boolean ok, String note) {
    }

    /**
     * 详情
     *
     * @param approved 本轮已经审批通过的人数
     * @param actions  当前用户能做的操作（Action 名称）
     */
    public record Detail(MocChange change, List<PssrItem> pssr, List<MocLog> logs, int approved, List<String> actions) {
    }

    /** 列表一行：变更 + 本轮审批进度 + 当前用户要不要处理 */
    public record Row(MocChange change, int approved, boolean todo, boolean restoreOverdue) {
    }

    public record Page(List<Row> records, long total) {
    }

    public record Overview(int assessing, int approving, int implementing, int pssr, int temporaryOpen, int restoreOverdue,
                           int closedThisYear, int myTodo) {
    }
}
