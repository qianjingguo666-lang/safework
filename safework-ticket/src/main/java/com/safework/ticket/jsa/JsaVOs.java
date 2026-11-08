package com.safework.ticket.jsa;

import com.safework.ticket.entity.Jsa;

import java.time.LocalDateTime;
import java.util.List;

/** JSA 的返回对象 */
public final class JsaVOs {

    private JsaVOs() {
    }

    /** 一个步骤和它的风险值、风险等级 */
    public record StepView(int seq, String step, String hazards, int likelihood, int severity, int risk, String level, String controls) {
    }

    /** 列表行：被多少张作业票引用、是否该复审 */
    public record Row(Jsa jsa, String maxLevel, long ticketCount, boolean reviewDue) {
    }

    public record Page(List<Row> records, long total) {
    }

    /** 引用这份 JSA 的作业票 */
    public record TicketRef(Long id, String ticketNo, String title, String status, LocalDateTime planStartTime) {
    }

    /** 详情；actions：EDIT、SUBMIT、APPROVE、REJECT、ARCHIVE、COPY、DELETE */
    public record Detail(Jsa jsa, List<StepView> steps, String maxLevel, boolean reviewDue, List<TicketRef> tickets, List<String> actions) {
    }

    /** 开作业票时可以选的（已发布、类型一致或通用） */
    public record Option(Long id, String jsaNo, String title, String ticketType, String maxLevel, String ppe, List<StepView> steps) {
    }

    public record Overview(int published, int reviewing, int reviewDue, int drafts) {
    }
}
