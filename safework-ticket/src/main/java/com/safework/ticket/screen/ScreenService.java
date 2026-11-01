package com.safework.ticket.screen;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.contractor.ContractorRules;
import com.safework.safety.emergency.EmergencyRules;
import com.safework.safety.entity.Contractor;
import com.safework.safety.entity.ContractorWorker;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.EmergencyPlan;
import com.safework.safety.entity.Equipment;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.PersonCert;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.equipment.EquipmentRules;
import com.safework.safety.mapper.ContractorMapper;
import com.safework.safety.mapper.ContractorWorkerMapper;
import com.safework.safety.mapper.EmergencyEventMapper;
import com.safework.safety.mapper.EmergencyPlanMapper;
import com.safework.safety.mapper.EquipmentMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.PersonCertMapper;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.entity.WorkTicketLog;
import com.safework.ticket.enums.TicketAction;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.WorkTicketLogMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 安全数据大屏：把各模块“现在”的状态汇总到一屏。
 * <p>
 * 只做计数和少量明细查询（每类最多几十行），全部带租户隔离；
 * 直接用各模块的 Mapper 统计全企业的数据，不走页面上“只看自己的”数据权限 —— 大屏本身就要求有 screen:view 权限。
 */
@Service
@RequiredArgsConstructor
public class ScreenService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final List<HazardStatus> OPEN = List.of(HazardStatus.REPORTED, HazardStatus.RECTIFYING, HazardStatus.PENDING_VERIFY);
    /** 动态里显示的作业票操作（创建、修改这种不显示） */
    private static final Set<TicketAction> FEED_ACTIONS = EnumSet.of(TicketAction.SUBMIT, TicketAction.APPROVE, TicketAction.REJECT,
            TicketAction.START, TicketAction.COMPLETE);
    private static final int TREND_DAYS = 14;

    private final WorkTicketMapper ticketMapper;
    private final WorkTicketLogMapper ticketLogMapper;
    private final HazardMapper hazardMapper;
    private final RiskPointMapper riskPointMapper;
    private final EmergencyEventMapper eventMapper;
    private final EmergencyPlanMapper planMapper;
    private final PersonCertMapper certMapper;
    private final EquipmentMapper equipmentMapper;
    private final ContractorMapper contractorMapper;
    private final ContractorWorkerMapper workerMapper;

    public ScreenVO overview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();

        // ---------- 作业 ----------
        List<WorkTicket> inProgress = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                .eq(WorkTicket::getStatus, TicketStatus.IN_PROGRESS)
                .orderByAsc(WorkTicket::getPlanEndTime)
                .last("LIMIT 50"));
        List<ScreenVO.Working> working = inProgress.stream().map(t -> new ScreenVO.Working(t.getId(), t.getTicketNo(),
                t.getTicketType().getLabel(), t.getTitle(), t.getLocation(), t.getGuardianName(), t.getContractorName(),
                t.getActualStartTime(), t.getPlanEndTime(), t.getPlanEndTime() != null && t.getPlanEndTime().isBefore(now))).toList();
        long ticketPending = ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery().eq(WorkTicket::getStatus, TicketStatus.PENDING));
        long ticketToday = ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery()
                .ge(WorkTicket::getCreateTime, today.atStartOfDay()));

        // ---------- 隐患 ----------
        long hazardOpen = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().in(Hazard::getStatus, OPEN));
        long hazardMajor = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().in(Hazard::getStatus, OPEN).eq(Hazard::getLevel, "MAJOR"));
        long hazardOverdue = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getStatus, HazardStatus.RECTIFYING).lt(Hazard::getDeadline, now));
        ScreenVO.Trend trend = hazardTrend(today);

        // ---------- 风险 ----------
        Map<String, Long> colors = new LinkedHashMap<>();
        for (RiskLevel l : RiskLevel.values()) {
            colors.put(l.name(), 0L);
        }
        for (Map<String, Object> row : riskPointMapper.selectMaps(new QueryWrapper<RiskPoint>()
                .select("risk_level AS lv", "COUNT(*) AS c").eq("status", 1).groupBy("risk_level"))) {
            colors.put(String.valueOf(row.get("lv")), num(row.get("c")));
        }
        long riskOverdue = riskPointMapper.selectCount(Wrappers.<RiskPoint>lambdaQuery()
                .eq(RiskPoint::getStatus, 1).lt(RiskPoint::getNextCheckTime, now));

        // ---------- 应急 ----------
        List<ScreenVO.Responding> responding = eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery()
                        .eq(EmergencyEvent::getStatus, EmergencyRules.RESPONDING)
                        .orderByDesc(EmergencyEvent::getResponseTime))
                .stream().map(e -> new ScreenVO.Responding(e.getId(), e.getTitle(), EmergencyRules.LEVEL_LABELS.get(e.getLevel()),
                        e.getLocation(), e.getCommanderName(), e.getResponseTime())).toList();
        EmergencyEvent last = eventMapper.selectOne(Wrappers.<EmergencyEvent>lambdaQuery()
                .orderByDesc(EmergencyEvent::getOccurTime).last("LIMIT 1"));
        Long safeDays = last == null ? null : ChronoUnit.DAYS.between(last.getOccurTime().toLocalDate(), today);

        ScreenVO.Kpi kpi = new ScreenVO.Kpi(working.size(), ticketPending, ticketToday, hazardOpen, hazardMajor, hazardOverdue, responding.size());
        return new ScreenVO(now, safeDays, kpi, working, colors, riskOverdue, trend, responding, warnings(today, now),
                feed());
    }

    /** 到期 / 逾期预警：只返回有数的 */
    private List<ScreenVO.Warning> warnings(LocalDate today, LocalDateTime now) {
        LocalDate soon = today.plusDays(30);
        List<ScreenVO.Warning> list = new ArrayList<>();
        add(list, "设备检验逾期", equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getStatus, EquipmentRules.IN_USE).lt(Equipment::getNextInspectDate, today)), "danger", "/safety/equipment");
        add(list, "设备从未检验", equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getStatus, EquipmentRules.IN_USE).isNull(Equipment::getNextInspectDate)), "danger", "/safety/equipment");
        add(list, "设备 30 天内到期检验", equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getStatus, EquipmentRules.IN_USE).between(Equipment::getNextInspectDate, today, soon)), "warning", "/safety/equipment");
        add(list, "人员证书已过期", certMapper.selectCount(Wrappers.<PersonCert>lambdaQuery()
                .lt(PersonCert::getValidUntil, today)), "danger", "/safety/cert");
        add(list, "人员证书 30 天内到期", certMapper.selectCount(Wrappers.<PersonCert>lambdaQuery()
                .between(PersonCert::getValidUntil, today, soon)), "warning", "/safety/cert");
        add(list, "风险点巡检逾期", riskPointMapper.selectCount(Wrappers.<RiskPoint>lambdaQuery()
                .eq(RiskPoint::getStatus, 1).lt(RiskPoint::getNextCheckTime, now)), "danger", "/safety/risk");
        add(list, "承包商黑名单", contractorMapper.selectCount(Wrappers.<Contractor>lambdaQuery()
                .eq(Contractor::getStatus, ContractorRules.BLACKLISTED)), "warning", "/safety/contractor");
        add(list, "承包商资质 / 保险快到期", contractorMapper.selectCount(Wrappers.<Contractor>lambdaQuery()
                .eq(Contractor::getStatus, ContractorRules.APPROVED)
                .and(w -> w.le(Contractor::getLicenseExpire, soon).or().le(Contractor::getInsuranceExpire, soon))), "warning", "/safety/contractor");
        add(list, "承包商人员培训过期", workerMapper.selectCount(Wrappers.<ContractorWorker>lambdaQuery()
                .eq(ContractorWorker::getStatus, 1)
                .and(w -> w.isNull(ContractorWorker::getTrainingDate).or()
                        .lt(ContractorWorker::getTrainingDate, today.minusMonths(ContractorRules.TRAINING_MONTHS)))), "danger", "/safety/contractor");
        // 预案演练逾期：预案只有几份到几十份，取出来按规则算
        long drillOverdue = planMapper.selectList(Wrappers.<EmergencyPlan>lambdaQuery().eq(EmergencyPlan::getStatus, 1)).stream()
                .map(p -> EmergencyRules.drillState(p.getPlanType(), p.getLastDrillTime(), today).state())
                .filter(s -> EmergencyRules.DRILL_NEVER.equals(s) || EmergencyRules.DRILL_OVERDUE.equals(s))
                .count();
        add(list, "应急预案未按期演练", drillOverdue, "warning", "/safety/emergency-plan");
        // 红色的排在前面
        list.sort(Comparator.comparing(w -> "danger".equals(w.level()) ? 0 : 1));
        return list;
    }

    private static void add(List<ScreenVO.Warning> list, String label, long count, String level, String link) {
        if (count > 0) {
            list.add(new ScreenVO.Warning(label, count, level, link));
        }
    }

    private ScreenVO.Trend hazardTrend(LocalDate today) {
        LocalDate first = today.minusDays(TREND_DAYS - 1L);
        Map<LocalDate, Long> reported = new HashMap<>();
        for (Map<String, Object> row : hazardMapper.selectMaps(new QueryWrapper<Hazard>()
                .select("DATE_FORMAT(create_time, '%Y-%m-%d') AS d", "COUNT(*) AS c")
                .ge("create_time", first.atStartOfDay())
                .groupBy("d"))) {
            reported.put(date(row.get("d")), num(row.get("c")));
        }
        Map<LocalDate, Long> closed = new HashMap<>();
        for (Map<String, Object> row : hazardMapper.selectMaps(new QueryWrapper<Hazard>()
                .select("DATE_FORMAT(verify_time, '%Y-%m-%d') AS d", "COUNT(*) AS c")
                .eq("status", HazardStatus.CLOSED.name())
                .ge("verify_time", first.atStartOfDay())
                .groupBy("d"))) {
            closed.put(date(row.get("d")), num(row.get("c")));
        }
        List<String> dates = new ArrayList<>();
        List<Long> r = new ArrayList<>();
        List<Long> c = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            dates.add(d.format(DAY));
            r.add(reported.getOrDefault(d, 0L));
            c.add(closed.getOrDefault(d, 0L));
        }
        return new ScreenVO.Trend(dates, r, c);
    }

    /** 最近动态：作业票流转、隐患上报、事故快报，按时间合并取最新 12 条 */
    private List<ScreenVO.Feed> feed() {
        List<ScreenVO.Feed> feed = new ArrayList<>();
        List<WorkTicketLog> logs = ticketLogMapper.selectList(Wrappers.<WorkTicketLog>lambdaQuery()
                .in(WorkTicketLog::getAction, FEED_ACTIONS)
                .orderByDesc(WorkTicketLog::getCreateTime)
                .last("LIMIT 12"));
        if (!logs.isEmpty()) {
            Map<Long, WorkTicket> tickets = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                            .in(WorkTicket::getId, logs.stream().map(WorkTicketLog::getTicketId).distinct().toList()))
                    .stream().collect(Collectors.toMap(WorkTicket::getId, Function.identity()));
            for (WorkTicketLog l : logs) {
                WorkTicket t = tickets.get(l.getTicketId());
                if (t != null) {
                    feed.add(new ScreenVO.Feed("TICKET", l.getOperatorName() + " " + l.getAction().getLabel() + "：" + t.getTicketType().getLabel()
                            + "「" + t.getTitle() + "」", l.getCreateTime()));
                }
            }
        }
        for (Hazard h : hazardMapper.selectList(Wrappers.<Hazard>lambdaQuery().orderByDesc(Hazard::getCreateTime).last("LIMIT 8"))) {
            feed.add(new ScreenVO.Feed("HAZARD", h.getReporterName() + " 上报" + ("MAJOR".equals(h.getLevel()) ? "重大" : "") + "隐患：" + h.getTitle(),
                    h.getCreateTime()));
        }
        for (EmergencyEvent e : eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery().orderByDesc(EmergencyEvent::getCreateTime).last("LIMIT 4"))) {
            feed.add(new ScreenVO.Feed("EMERGENCY", e.getReporterName() + " 事故快报：" + e.getTitle(), e.getCreateTime()));
        }
        return feed.stream()
                .filter(f -> f.time() != null)
                .sorted(Comparator.comparing(ScreenVO.Feed::time).reversed())
                .limit(12)
                .toList();
    }

    private static long num(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    /**
     * 日期用 DATE_FORMAT 在数据库里转成字符串再解析：直接取 DATE 类型时，驱动会按 serverTimezone 和 JVM 时区换算，
     * 两个时区不一样（比如电脑系统时区不是北京时间）会差一天
     */
    private static LocalDate date(Object o) {
        return LocalDate.parse(String.valueOf(o).substring(0, 10));
    }
}
