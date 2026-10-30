package com.safework.ticket.print;

import com.safework.ticket.enums.TicketStatus;

import java.time.LocalDateTime;

/**
 * 扫码验真的结论（纯计算，方便单元测试）：现场检查人员最关心“这张票现在能不能作业”。
 *
 * @param level OK 可以作业 / WARN 需要注意 / DANGER 不能作业
 */
public record TicketVerdict(String level, String text) {

    public static final String OK = "OK";
    public static final String WARN = "WARN";
    public static final String DANGER = "DANGER";

    public static TicketVerdict of(TicketStatus status, LocalDateTime planEnd, LocalDateTime now) {
        boolean expired = planEnd != null && now.isAfter(planEnd);
        return switch (status) {
            case APPROVED -> expired
                    ? new TicketVerdict(DANGER, "已过计划时间未开工，作业票失效，不能作业")
                    : new TicketVerdict(OK, "有效：已批准，监护人确认现场措施后可以开工");
            case IN_PROGRESS -> expired
                    ? new TicketVerdict(DANGER, "作业已超过计划结束时间，请立即核实现场人员安全")
                    : new TicketVerdict(OK, "有效：作业中");
            case COMPLETED -> new TicketVerdict(WARN, "作业已完工关闭，不能再用这张票作业");
            case CANCELLED -> new TicketVerdict(DANGER, "作业票已作废，不能作业");
            case DRAFT, PENDING, REJECTED -> new TicketVerdict(DANGER, "作业票未批准（" + status.getLabel() + "），不能作业");
        };
    }
}
