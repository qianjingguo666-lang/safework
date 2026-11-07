package com.safework.safety.injury;

import com.safework.safety.entity.InjuryCase;
import com.safework.safety.entity.InjuryCost;

import java.math.BigDecimal;
import java.util.List;

/** 工伤管理的返回对象 */
public final class InjuryVOs {

    private InjuryVOs() {
    }

    public record Row(InjuryCase injury, InjuryRules.Deadline deadline, BigDecimal costTotal) {
    }

    public record Page(List<Row> records, long total) {
    }

    /**
     * 详情
     *
     * @param lumpSum 按伤残等级和本人工资算出的一次性伤残补助金（参考）
     */
    public record Detail(InjuryCase injury, InjuryRules.Deadline deadline, List<InjuryCost> costs, BigDecimal fundTotal,
                         BigDecimal companyTotal, String gradeLabel, Integer lumpSumMonths, BigDecimal lumpSum, List<String> actions) {
    }

    /** 概况：今年工伤人数、待申请认定（其中超期）、处理中、今年费用（基金 / 单位） */
    public record Overview(int yearCases, int pendingApply, int overdueApply, int open, BigDecimal yearFund, BigDecimal yearCompany) {
    }
}
