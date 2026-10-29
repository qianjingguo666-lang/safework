package com.safework.ticket.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.ConfinedSpaceMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据看板统计。全部用 GROUP BY 在数据库里聚合，不把明细查到内存里再算；
 * SQL 不写 tenant_id，租户插件自动加上，所以每个企业只能看到自己的数据。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    /** 未闭环的隐患状态 */
    private static final List<HazardStatus> OPEN = List.of(HazardStatus.REPORTED, HazardStatus.RECTIFYING, HazardStatus.PENDING_VERIFY);

    private final WorkTicketMapper ticketMapper;
    private final HazardMapper hazardMapper;
    private final ConfinedSpaceMapper spaceMapper;

    public DashboardVO overview(int days) {
        int n = Math.max(7, Math.min(days, 90));
        LocalDate today = LocalDate.now();
        LocalDate first = today.minusDays(n - 1L);
        LocalDateTime since = first.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        DashboardVO vo = new DashboardVO();
        List<LocalDate> dayList = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            dayList.add(d);
            vo.getDates().add(d.format(DAY));
        }

        // ---------- 作业票 ----------
        Map<String, Map<LocalDate, Long>> byType = new HashMap<>();
        for (Map<String, Object> row : ticketMapper.selectMaps(new QueryWrapper<WorkTicket>()
                .select("DATE(create_time) AS d", "ticket_type AS t", "COUNT(*) AS c")
                .ge("create_time", since)
                .groupBy("DATE(create_time)", "ticket_type"))) {
            byType.computeIfAbsent(str(row.get("t")), k -> new HashMap<>()).put(date(row.get("d")), num(row.get("c")));
        }
        long ticketTotal = 0;
        for (TicketType type : TicketType.values()) {
            List<Long> series = fill(dayList, byType.getOrDefault(type.name(), Map.of()));
            ticketTotal += series.stream().mapToLong(Long::longValue).sum();
            vo.getTicketTrend().put(type.getLabel(), series);
        }
        vo.getKpi().setTicketTotal(ticketTotal);

        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (Map<String, Object> row : ticketMapper.selectMaps(new QueryWrapper<WorkTicket>()
                .select("status AS s", "COUNT(*) AS c")
                .ge("create_time", since)
                .groupBy("status"))) {
            statusCount.put(str(row.get("s")), num(row.get("c")));
        }
        for (TicketStatus s : TicketStatus.values()) {
            long c = statusCount.getOrDefault(s.name(), 0L);
            if (c > 0) {
                vo.getTicketStatus().add(new DashboardVO.NameValue(s.getLabel(), c));
            }
        }
        // 进行中、待审批看的是“现在”，不限统计周期
        vo.getKpi().setTicketInProgress(ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery()
                .eq(WorkTicket::getStatus, TicketStatus.IN_PROGRESS)));
        vo.getKpi().setTicketPending(ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery()
                .eq(WorkTicket::getStatus, TicketStatus.PENDING)));

        // ---------- 隐患 ----------
        Map<LocalDate, Long> reported = new HashMap<>();
        for (Map<String, Object> row : hazardMapper.selectMaps(new QueryWrapper<Hazard>()
                .select("DATE(create_time) AS d", "COUNT(*) AS c")
                .ge("create_time", since)
                .groupBy("DATE(create_time)"))) {
            reported.put(date(row.get("d")), num(row.get("c")));
        }
        Map<LocalDate, Long> closed = new HashMap<>();
        for (Map<String, Object> row : hazardMapper.selectMaps(new QueryWrapper<Hazard>()
                .select("DATE(verify_time) AS d", "COUNT(*) AS c")
                .eq("status", HazardStatus.CLOSED.name())
                .ge("verify_time", since)
                .groupBy("DATE(verify_time)"))) {
            closed.put(date(row.get("d")), num(row.get("c")));
        }
        vo.setHazardReported(fill(dayList, reported));
        vo.setHazardClosed(fill(dayList, closed));

        for (Map<String, Object> row : hazardMapper.selectMaps(new QueryWrapper<Hazard>()
                .select("category AS name",
                        "SUM(CASE WHEN status IN ('REPORTED','RECTIFYING','PENDING_VERIFY') THEN 1 ELSE 0 END) AS open_cnt",
                        "SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END) AS closed_cnt")
                .groupBy("category"))) {
            vo.getHazardCategory().add(new DashboardVO.CategoryStat(str(row.get("name")),
                    num(row.get("open_cnt")), num(row.get("closed_cnt"))));
        }

        vo.getKpi().setHazardOpen(hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().in(Hazard::getStatus, OPEN)));
        vo.getKpi().setHazardMajorOpen(hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .in(Hazard::getStatus, OPEN).eq(Hazard::getLevel, "MAJOR")));
        vo.getKpi().setHazardOverdue(hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getStatus, HazardStatus.RECTIFYING).lt(Hazard::getDeadline, now)));

        List<Map<String, Object>> onTime = hazardMapper.selectMaps(new QueryWrapper<Hazard>()
                .select("COUNT(*) AS total", "SUM(CASE WHEN rectify_time <= deadline THEN 1 ELSE 0 END) AS ontime")
                .eq("status", HazardStatus.CLOSED.name())
                .isNotNull("deadline")
                .isNotNull("rectify_time"));
        if (!onTime.isEmpty() && onTime.get(0) != null) {
            long total = num(onTime.get(0).get("total"));
            if (total > 0) {
                double rate = num(onTime.get(0).get("ontime")) * 100.0 / total;
                vo.getKpi().setRectifyOnTimeRate(Math.round(rate * 10) / 10.0);
            }
        }

        // ---------- 受限空间 ----------
        Map<String, Long> risk = new HashMap<>();
        for (Map<String, Object> row : spaceMapper.selectMaps(new QueryWrapper<ConfinedSpace>()
                .select("risk_level AS r", "COUNT(*) AS c")
                .eq("status", 1)
                .groupBy("risk_level"))) {
            risk.put(str(row.get("r")), num(row.get("c")));
        }
        vo.getSpaceRisk().add(new DashboardVO.NameValue("高风险", risk.getOrDefault("HIGH", 0L)));
        vo.getSpaceRisk().add(new DashboardVO.NameValue("中风险", risk.getOrDefault("MEDIUM", 0L)));
        vo.getSpaceRisk().add(new DashboardVO.NameValue("低风险", risk.getOrDefault("LOW", 0L)));
        vo.getKpi().setSpaceTotal(risk.values().stream().mapToLong(Long::longValue).sum());
        vo.getKpi().setSpaceHighRisk(risk.getOrDefault("HIGH", 0L));
        List<Map<String, Object>> working = ticketMapper.selectMaps(new QueryWrapper<WorkTicket>()
                .select("COUNT(DISTINCT space_id) AS c")
                .eq("status", TicketStatus.IN_PROGRESS.name())
                .isNotNull("space_id"));
        vo.getKpi().setSpaceWorking(working.isEmpty() || working.get(0) == null ? 0 : num(working.get(0).get("c")));
        return vo;
    }

    /** 没有数据的日期补 0，保证折线图每天都有点 */
    static List<Long> fill(List<LocalDate> days, Map<LocalDate, Long> values) {
        List<Long> list = new ArrayList<>(days.size());
        for (LocalDate d : days) {
            list.add(values.getOrDefault(d, 0L));
        }
        return list;
    }

    /** JDBC 驱动返回的 DATE 可能是 java.sql.Date 或 LocalDate，toString 都是 yyyy-MM-dd */
    private static LocalDate date(Object v) {
        return v == null ? null : LocalDate.parse(v.toString().substring(0, 10));
    }

    /** COUNT 返回 Long，SUM 返回 BigDecimal，统一转成 long */
    private static long num(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}
