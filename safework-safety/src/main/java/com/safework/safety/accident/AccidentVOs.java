package com.safework.safety.accident;

import com.safework.safety.entity.AccidentInvestigation;
import com.safework.safety.entity.AccidentMeasure;
import com.safework.safety.entity.AccidentPunish;
import com.safework.safety.entity.EmergencyEvent;

import java.time.LocalDateTime;
import java.util.List;

/** 事故调查的返回对象 */
public final class AccidentVOs {

    private AccidentVOs() {
    }

    /** 列表一行：调查 + 措施进度 + “四不放过”满足了几项 */
    public record Row(AccidentInvestigation investigation, int measureTotal, int measureDone, int measureOverdue, int fourOk) {
    }

    public record Page(List<Row> records, long total) {
    }

    /**
     * 详情
     *
     * @param canEdit  能填写调查内容（管理员或调查组长，调查中）
     * @param canClose 能结案（管理员，调查中）
     */
    public record Detail(AccidentInvestigation investigation, EmergencyEvent event, List<AccidentMeasure> measures,
                         List<AccidentPunish> punishes, List<AccidentRules.Check> checks, boolean canEdit, boolean canClose) {
    }

    /** 可以启动调查的事件（还没有调查的） */
    public record Candidate(Long eventId, String eventNo, String title, String level, String accidentType,
                            LocalDateTime occurTime, String status, int dead, int seriousInjured, int minorInjured) {
    }

    public record Overview(int investigating, int overdue, int openMeasures, int overdueMeasures, int closedThisYear) {
    }

    /** 结案后发出的事件：AI 模块收到后把事故案例写进知识库 */
    public record ClosedEvent(Long investigationId, String title, String text) {
    }
}
