package com.safework.ticket.dashboard;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 数据看板：首页指标卡 + 图表数据（前端直接喂给 ECharts） */
@Data
public class DashboardVO {

    private Kpi kpi = new Kpi();
    /** 日期轴，如 10-01 … 10-30 */
    private List<String> dates = new ArrayList<>();
    /** 每天新建的作业票，按作业类型分组：类型中文名 → 每天数量 */
    private Map<String, List<Long>> ticketTrend = new LinkedHashMap<>();
    /** 每天新上报 / 闭环的隐患 */
    private List<Long> hazardReported = new ArrayList<>();
    private List<Long> hazardClosed = new ArrayList<>();
    /** 作业票状态分布 */
    private List<NameValue> ticketStatus = new ArrayList<>();
    /** 隐患按类别：未闭环 / 已闭环 */
    private List<CategoryStat> hazardCategory = new ArrayList<>();
    /** 受限空间按风险等级 */
    private List<NameValue> spaceRisk = new ArrayList<>();

    @Data
    public static class Kpi {
        /** 统计周期内新建的作业票 */
        private long ticketTotal;
        private long ticketInProgress;
        private long ticketPending;
        /** 未闭环隐患（待指派、整改中、待验收） */
        private long hazardOpen;
        private long hazardOverdue;
        private long hazardMajorOpen;
        /** 整改及时率（%）：已闭环的隐患里，在期限内完成整改的比例；没有数据时为空 */
        private Double rectifyOnTimeRate;
        private long spaceTotal;
        private long spaceHighRisk;
        /** 现在有人在里面作业的受限空间数 */
        private long spaceWorking;
    }

    public record NameValue(String name, long value) {
    }

    public record CategoryStat(String name, long open, long closed) {
    }
}
