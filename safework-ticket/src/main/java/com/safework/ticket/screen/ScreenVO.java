package com.safework.ticket.screen;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 安全数据大屏：一个接口返回整屏数据，前端每 30 秒刷新一次
 *
 * @param safeDays    距离上一次事故（应急事件）的天数，从来没有事故时为 null
 * @param working     正在作业的作业票
 * @param riskColors  风险四色分布：重大（红）、较大（橙）、一般（黄）、低（蓝）的风险点数
 * @param hazardTrend 近 14 天隐患上报 / 闭环趋势
 * @param responding  正在应急响应的事件
 * @param warnings    到期 / 逾期预警，只返回数量大于 0 的
 * @param feed        最近动态（作业票流转、隐患上报、事故快报）
 */
public record ScreenVO(@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime serverTime,
                       Long safeDays, Kpi kpi, List<Working> working, Map<String, Long> riskColors, long riskCheckOverdue,
                       Trend hazardTrend, List<Responding> responding, List<Warning> warnings, List<Feed> feed) {

    public record Kpi(long ticketInProgress, long ticketPending, long ticketToday, long hazardOpen, long hazardMajorOpen,
                      long hazardOverdue, long emergencyResponding) {
    }

    public record Working(Long id, String ticketNo, String type, String title, String location, String guardianName,
                          String contractorName,
                          @JsonFormat(pattern = "MM-dd HH:mm") LocalDateTime startTime,
                          @JsonFormat(pattern = "MM-dd HH:mm") LocalDateTime planEndTime, boolean overtime) {
    }

    public record Trend(List<String> dates, List<Long> reported, List<Long> closed) {
    }

    public record Responding(Long id, String title, String level, String location, String commanderName,
                             @JsonFormat(pattern = "MM-dd HH:mm") LocalDateTime responseTime) {
    }

    /**
     * @param level danger / warning
     * @param link  点击跳转的页面
     */
    public record Warning(String label, long count, String level, String link) {
    }

    /**
     * @param kind TICKET / HAZARD / EMERGENCY
     */
    public record Feed(String kind, String text, @JsonFormat(pattern = "MM-dd HH:mm") LocalDateTime time) {
    }
}
