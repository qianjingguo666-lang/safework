package com.safework.safety.cost;

import com.safework.safety.entity.CostExpense;
import com.safework.safety.entity.CostPlan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 安全投入的返回对象 */
public final class CostVOs {

    private CostVOs() {
    }

    /** 一个使用类别：预算、已使用、笔数、使用率、状态（NORMAL / OVER / UNPLANNED） */
    public record CategoryRow(String code, String label, String description, BigDecimal budget, BigDecimal used, int count,
                              Double rate, String state) {
    }

    /** 一个月：当月支出、累计支出、累计应提（逐月平均）+ 上年结转 = 累计可用 */
    public record Month(int month, BigDecimal used, BigDecimal cumUsed, BigDecimal cumAvailable) {
    }

    /**
     * 年度报表
     *
     * @param available     当年可用 = 上年结转 + 本年应提
     * @param balance       余额 = 可用 - 已使用（负数年末要补提）
     * @param accruedToDate 按逐月平均，截至本月应已提取
     * @param timeProgress  时间进度（%）
     * @param lagging       使用进度偏慢
     * @param canPause      年初结余达到上年应提 3 倍，可以暂停提取
     */
    public record Report(int year, LocalDate asOf, boolean hasPlan, CostPlan plan, CostRules.Accrual accrual,
                         BigDecimal available, BigDecimal used, BigDecimal balance, Double usageRate, BigDecimal accruedToDate,
                         double timeProgress, boolean lagging, boolean canPause, int overCount, int expenseCount,
                         BigDecimal hazardUsed, List<CategoryRow> categories, List<Month> months, List<String> alerts) {
    }

    /** 编制计划的表单：已有计划，或者带出建议的上年结转（上年计划的余额） */
    public record PlanForm(int year, CostPlan plan, BigDecimal suggestedCarry, List<BudgetRow> budgets) {
    }

    public record BudgetRow(String code, String label, String description, BigDecimal amount) {
    }

    /** 保存结果 + 提示（不拦截，例如预算超过可用额度、这一笔让分类超预算） */
    public record SaveResult(Long id, String warning) {
    }

    public record ExpensePage(List<CostExpense> records, long total, BigDecimal sum) {
    }
}
