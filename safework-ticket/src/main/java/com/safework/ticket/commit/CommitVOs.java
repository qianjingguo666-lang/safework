package com.safework.ticket.commit;

import com.safework.ticket.entity.RiskCommitment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 风险研判承诺的返回对象 */
public final class CommitVOs {

    private CommitVOs() {
    }

    /** 系统统计：今天的特殊作业票、重大危险源、超储、未整改隐患、未恢复的临时变更 */
    public record Stats(int hotWork, int confined, int height, int majorSources, int overstock, int openMajorHazards, int openHazards,
                        int mocTemporary) {
    }

    /** 一个研判项目和结果（还没研判时 ok 为 null） */
    public record ItemView(String code, String text, Boolean ok, String note, String measure) {
    }

    /** 一个方面的研判 */
    public record AspectView(String aspect, String label, String judgerName, LocalDateTime judgeTime, int abnormal, List<ItemView> items) {
    }

    /**
     * 今天
     *
     * @param commitment 还没有人研判时为 null
     * @param stats      已承诺的是承诺时固定下来的，否则是现在的
     * @param lastUnits  上一次填的装置数（生产负责人研判时带出来）
     */
    public record Today(LocalDate date, RiskCommitment commitment, List<AspectView> aspects, Stats stats, List<String> missing,
                        boolean judgeLate, boolean commitLate, boolean canJudge, boolean canSign, CommitRules.Units lastUnits) {
    }

    /** 公告：异常项、研判人、主要负责人签名 */
    public record Board(RiskCommitment commitment, Stats stats, List<AspectView> aspects, String signImage) {
    }

    public record HistoryRow(LocalDate date, String status, Long id, LocalDateTime commitTime, boolean onTime, Integer abnormal, String committerName) {
    }

    /** 某月记录和按时承诺率 */
    public record History(String month, List<HistoryRow> rows, int days, int onTimeDays, BigDecimal rate) {
    }

    /** 概况：不依赖登录（定时任务、大屏、智能体用） */
    public record Overview(LocalDate date, String status, List<String> missing, boolean committed, boolean judgeLate, boolean commitLate,
                           Integer abnormal, int monthDays, int monthOnTime, BigDecimal monthRate) {
    }
}
