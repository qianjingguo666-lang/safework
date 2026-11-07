package com.safework.safety.drill;

import com.safework.safety.entity.DrillParticipant;
import com.safework.safety.entity.DrillProblem;
import com.safework.safety.entity.EmergencyDrill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 应急演练的返回对象 */
public final class DrillVOs {

    private DrillVOs() {
    }

    /** 列表行：参演人数、已签到人数、发现问题数、已转整改数 */
    public record Row(EmergencyDrill drill, int participants, int signed, int problems, int hazards) {
    }

    /** 评估项目打分（score 为 null 表示不适用；还没评估时 score、remark 都是 null） */
    public record ScoreRow(String code, String name, int max, String hint, BigDecimal score, String remark) {
    }

    /** 问题和对应隐患的状态 */
    public record ProblemRow(DrillProblem problem, String hazardStatus, String hazardStatusLabel) {
    }

    /**
     * 详情
     *
     * @param mine    当前用户在参演名单里的记录（不在为 null）
     * @param canSign 当前用户现在能不能签到
     * @param actions 当前用户能做的操作：EDIT、CANCEL、COMPLETE、EVALUATE、TO_HAZARD
     */
    public record Detail(EmergencyDrill drill, String planType, String planCode, List<DrillParticipant> participants,
                         List<ScoreRow> scores, List<ProblemRow> problems, DrillParticipant mine, boolean canSign, List<String> actions) {
    }

    /** 年度演练计划：一份启用的预案一行 */
    public record YearRow(Long planId, String planCode, String planTitle, String planType, int required, int done, int planned,
                          String state, LocalDateTime lastDrillTime, String reviseNote, List<YearDrill> drills) {
    }

    public record YearDrill(Long id, String title, String status, LocalDate date, String grade) {
    }

    public record Annual(int year, List<YearRow> rows, int plans, int done, int missing) {
    }

    /** 我的演练 */
    public record Mine(EmergencyDrill drill, String role, LocalDateTime signTime, Integer attended, boolean canSign) {
    }

    /** 概况（大屏、智能体、定时任务用，不依赖登录） */
    public record Overview(int year, int yearDone, int yearMissingPlans, List<EmergencyDrill> upcoming, List<EmergencyDrill> pendingEvaluate,
                           List<EmergencyDrill> recordLate, List<EmergencyDrill> recentEvaluated, int openProblems) {
    }
}
