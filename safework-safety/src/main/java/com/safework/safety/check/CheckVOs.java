package com.safework.safety.check;

import com.safework.safety.entity.CheckTask;

import java.util.List;
import java.util.Map;

/** 安全检查的返回对象 */
public final class CheckVOs {

    private CheckVOs() {
    }

    /**
     * 统计（安全员看全部，其他人看自己的）
     *
     * @param pending     待检查（含逾期）
     * @param overdue     逾期未检查
     * @param monthDone   本月已完成
     * @param monthFail   本月发现的不合格项
     * @param monthHazard 本月转成的隐患
     * @param onTimeRate  本月到期任务的按时完成率（%），本月还没有到期的任务时为空
     */
    public record Overview(int pending, int overdue, int monthDone, int monthFail, int monthHazard, Integer onTimeRate) {
    }

    /** 任务详情：任务 + 检查项（待检查时）或逐项结果（已完成时）+ 当前用户能不能检查 */
    public record TaskDetail(CheckTask task, List<Map<String, Object>> items, boolean canSubmit) {
    }

    /** 提交结果 */
    public record SubmitResult(int failCount, List<String> hazardNos) {
    }
}
