package com.safework.ticket.visit;

import com.safework.ticket.entity.VisitPass;

import java.util.List;

/** 外来人员入厂的返回对象 */
public final class VisitVOs {

    private VisitVOs() {
    }

    /**
     * 列表 / 门岗一行
     *
     * @param problems 危化品车辆不符合入厂条件的地方（空表示没问题）
     * @param overstay 过了预约日期还在厂
     */
    public record Row(VisitPass pass, List<String> problems, boolean overstay) {
    }

    public record Page(List<Row> records, long total) {
    }

    public record Detail(VisitPass pass, List<String> problems, boolean overstay, List<String> notice,
                         boolean canApprove, boolean canCancel, boolean canGate) {
    }

    /** 概况：今日预约、在厂人数（按类型）、危化品车辆在厂、待审批、超时未离厂 */
    public record Overview(int today, int onsitePeople, int onsiteVisitors, int onsiteVehicles, int onsiteHazmat, int pending, int overstay) {
    }

    /** 入厂结果：车辆还要提醒的事项 */
    public record EnterResult(String passNo, String tip) {
    }
}
