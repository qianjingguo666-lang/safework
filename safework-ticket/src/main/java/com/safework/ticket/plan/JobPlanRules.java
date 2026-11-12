package com.safework.ticket.plan;

import com.safework.ticket.enums.TicketType;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class JobPlanRules {
    public static final String PLANNED = "PLANNED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final Map<String, String> STATUSES = Map.of(
            PLANNED, "待开始", IN_PROGRESS, "进行中", COMPLETED, "已完成", CANCELLED, "已取消");
    public static final Map<String, String> TICKET_TYPES = Arrays.stream(TicketType.values())
            .collect(Collectors.toMap(Enum::name, TicketType::getLabel));

    private JobPlanRules() {}

    public static String validate(String ticketType, LocalDateTime start, LocalDateTime end) {
        if (!TICKET_TYPES.containsKey(ticketType)) return "作业类型不正确";
        if (start == null || end == null || !end.isAfter(start)) return "计划结束时间必须晚于开始时间";
        return null;
    }

    public static String nextStatus(String current, String action) {
        if ("start".equals(action) && PLANNED.equals(current)) return IN_PROGRESS;
        if ("complete".equals(action) && IN_PROGRESS.equals(current)) return COMPLETED;
        if ("cancel".equals(action) && PLANNED.equals(current)) return CANCELLED;
        return null;
    }
}