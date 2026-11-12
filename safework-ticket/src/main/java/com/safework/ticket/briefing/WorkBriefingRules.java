package com.safework.ticket.briefing;

public final class WorkBriefingRules {
    public static final String PENDING = "PENDING";
    public static final String COMPLETED = "COMPLETED";
    public static final String APPLICANT = "APPLICANT";
    public static final String GUARDIAN = "GUARDIAN";
    public static final String MEMBER = "MEMBER";

    private WorkBriefingRules() {}

    public static boolean canStart(String status, int attendeeCount, int signedCount) {
        return COMPLETED.equals(status) && attendeeCount > 0 && attendeeCount == signedCount;
    }

    public static String snapshot(String ticketNo, String title, String ticketType, String location,
                                  String workContent, String jsaTitle, String safetyMeasures) {
        return String.join("\n",
                "作业票：" + safe(ticketNo),
                "作业名称：" + safe(title),
                "作业类型：" + safe(ticketType),
                "作业地点：" + safe(location),
                "作业内容：" + safe(workContent),
                "JSA：" + safe(jsaTitle),
                "安全措施与风险控制：",
                safe(safetyMeasures));
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "无" : s.trim();
    }
}