package com.safework.ticket.kpi;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.safety.check.CheckRules;
import com.safework.safety.entity.CheckTask;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.Equipment;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.MeetingTeam;
import com.safework.safety.entity.ShiftMeeting;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.equipment.EquipmentRules;
import com.safework.safety.mapper.CheckTaskMapper;
import com.safework.safety.mapper.EmergencyEventMapper;
import com.safework.safety.mapper.EquipmentMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.MeetingTeamMapper;
import com.safework.safety.mapper.ShiftMeetingMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.KpiTarget;
import com.safework.ticket.entity.RespBook;
import com.safework.ticket.entity.RespSign;
import com.safework.ticket.mapper.KpiTargetMapper;
import com.safework.ticket.mapper.RespBookMapper;
import com.safework.ticket.mapper.RespSignMapper;
import com.safework.ticket.resp.RespRules;
import com.safework.training.entity.TrainExamRecord;
import com.safework.training.enums.RecordStatus;
import com.safework.training.mapper.TrainExamRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 安全目标与绩效考核：年度目标（目标值 + 权重）→ 系统从各模块自动统计实际值（年初至今 + 每月）→ 亮灯、打分。
 * <p>
 * 一年的数据一次查出来（只查需要的列），每月的值在内存里分段计算，避免 8 个指标 × 12 个月 = 96 次查询。
 * 这个类不用登录信息（除了保存目标时取操作人），定时任务也能直接调用。
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    public static final String PERM_LIST = "kpi:list";
    public static final String PERM_EDIT = "kpi:edit";
    public static final String LINK = "/safety/kpi";

    private final KpiTargetMapper targetMapper;
    private final HazardMapper hazardMapper;
    private final CheckTaskMapper checkTaskMapper;
    private final MeetingTeamMapper teamMapper;
    private final ShiftMeetingMapper meetingMapper;
    private final TrainExamRecordMapper examRecordMapper;
    private final RespBookMapper bookMapper;
    private final RespSignMapper signMapper;
    private final EquipmentMapper equipmentMapper;
    private final EmergencyEventMapper eventMapper;
    private final UserService userService;

    /** 一个统计值：比例类有分子分母；次数类只有 value */
    record Measure(Double value, Long numerator, Long denominator) {
        static final Measure NONE = new Measure(null, null, null);

        static Measure ratio(long num, long den) {
            return new Measure(KpiRules.rate(num, den), num, den);
        }
    }

    /** 一年的原始数据 */
    record YearData(List<Hazard> hazards, List<CheckTask> checks, Map<Long, LocalDate> teams, List<ShiftMeeting> meetings,
                    List<TrainExamRecord> exams, List<RespSign> signs, List<Equipment> equipment, List<EmergencyEvent> events) {
    }

    // ============================== 目标 ==============================

    public List<KpiVOs.TargetRow> targets(int year) {
        Map<String, KpiTarget> saved = saved(year);
        return KpiRules.METRICS.stream().map(m -> {
            KpiTarget t = saved.get(m.code());
            return new KpiVOs.TargetRow(m.code(), m.label(), m.unit(), m.direction(),
                    t == null ? m.defaultTarget() : t.getTargetValue(), t == null ? m.defaultWeight() : t.getWeight());
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveTargets(KpiDTOs.Targets dto) {
        int thisYear = LocalDate.now().getYear();
        if (dto.getYear() < thisYear - 1 || dto.getYear() > thisYear + 1) {
            throw new BizException("只能设置去年、今年、明年的目标");
        }
        Map<String, Double> targets = new HashMap<>();
        Map<String, Integer> weights = new HashMap<>();
        for (KpiDTOs.TargetItem i : dto.getItems()) {
            if (!KpiRules.BY_CODE.containsKey(i.getCode())) {
                throw new BizException("指标不存在：" + i.getCode());
            }
            targets.put(i.getCode(), i.getTarget());
            weights.put(i.getCode(), i.getWeight());
        }
        String error = KpiRules.checkTargets(targets, weights);
        if (error != null) {
            throw new BizException(error);
        }
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        Map<String, KpiTarget> saved = saved(dto.getYear());
        for (KpiRules.Metric m : KpiRules.METRICS) {
            KpiTarget t = saved.get(m.code());
            boolean create = t == null;
            if (create) {
                t = new KpiTarget();
                t.setYear(dto.getYear());
                t.setMetricCode(m.code());
            }
            t.setTargetValue(targets.get(m.code()));
            t.setWeight(weights.get(m.code()));
            t.setUpdaterName(me.getNickname());
            if (create) {
                targetMapper.insert(t);
            } else {
                targetMapper.updateById(t);
            }
        }
    }

    // ============================== 年度报告 ==============================

    public KpiVOs.Report report(int year) {
        LocalDate today = LocalDate.now();
        if (year < 2000 || year > today.getYear() + 1) {
            throw new BizException("年度不正确");
        }
        Map<String, KpiTarget> saved = saved(year);
        YearData data = load(year);
        LocalDate[] ytd = KpiRules.yearRange(year, today);
        List<KpiVOs.Item> items = new ArrayList<>();
        for (KpiRules.Metric m : KpiRules.METRICS) {
            KpiTarget t = saved.get(m.code());
            double target = t == null ? m.defaultTarget() : t.getTargetValue();
            int weight = t == null ? m.defaultWeight() : t.getWeight();
            Measure me = year > today.getYear() ? Measure.NONE : measure(m.code(), data, ytd[0], ytd[1], today);
            List<Double> months = new ArrayList<>();
            if (!m.snapshot()) {
                for (int i = 1; i <= 12; i++) {
                    LocalDate[] r = KpiRules.monthRange(year, i, today);
                    months.add(r == null ? null : measure(m.code(), data, r[0], r[1], today).value());
                }
            }
            items.add(new KpiVOs.Item(m.code(), m.label(), m.unit(), m.direction(), m.source(), m.formula(), m.snapshot(),
                    target, weight, me.value(), me.numerator(), me.denominator(),
                    KpiRules.light(m.direction(), me.value(), target), KpiRules.score(m.direction(), me.value(), target, weight), months));
        }
        Double total = KpiRules.total(items.stream().map(KpiVOs.Item::score).toList(), items.stream().map(KpiVOs.Item::weight).toList());
        LocalDate asOf = ytd[1].minusDays(1);
        return new KpiVOs.Report(year, asOf.toString(), saved.isEmpty(), total,
                count(items, KpiRules.GREEN), count(items, KpiRules.YELLOW), count(items, KpiRules.RED), items);
    }

    // ============================== 计算 ==============================

    Measure measure(String code, YearData d, LocalDate from, LocalDate to, LocalDate today) {
        LocalDateTime f = from.atStartOfDay();
        LocalDateTime t = to.atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        return switch (code) {
            case "HAZARD_ONTIME" -> ratio(d.hazards(),
                    h -> h.getDeadline() != null && in(h.getDeadline(), f, t) && h.getDeadline().isBefore(now),
                    h -> h.getRectifyTime() != null && !h.getRectifyTime().isAfter(h.getDeadline()));
            case "HAZARD_CLOSE" -> ratio(d.hazards(), h -> in(h.getCreateTime(), f, t), h -> h.getStatus() == HazardStatus.CLOSED);
            case "CHECK_ONTIME" -> ratio(d.checks(),
                    c -> !c.getDueDate().isBefore(from) && c.getDueDate().isBefore(to) && c.getDueDate().isBefore(today),
                    c -> CheckRules.DONE.equals(c.getStatus()) && c.getFinishTime() != null && !c.getFinishTime().toLocalDate().isAfter(c.getDueDate()));
            case "MEETING_RATE" -> {
                // 每个班组从“成立那天”起才应该开班前会，年中新建的班组不算之前的天数
                LocalDate end = to.isAfter(today.plusDays(1)) ? today.plusDays(1) : to;
                long due = 0;
                for (LocalDate start : d.teams().values()) {
                    LocalDate s = start.isAfter(from) ? start : from;
                    due += s.isBefore(end) ? KpiRules.days(s, end).size() : 0;
                }
                Set<String> held = new HashSet<>();
                d.meetings().stream().filter(m -> d.teams().containsKey(m.getTeamId())
                                && !m.getMeetingDate().isBefore(from) && m.getMeetingDate().isBefore(end))
                        .forEach(m -> held.add(m.getTeamId() + ":" + m.getMeetingDate()));
                yield Measure.ratio(held.size(), due);
            }
            case "TRAINING_PASS" -> ratio(d.exams(), e -> in(e.getSubmitTime(), f, t), e -> Integer.valueOf(1).equals(e.getPassed()));
            case "RESP_SIGN" -> ratio(d.signs(), s -> s.getCreateTime() != null && s.getCreateTime().isBefore(t),
                    s -> RespRules.SIGNED.equals(s.getStatus()) && s.getSignTime() != null && s.getSignTime().isBefore(t));
            case "EQUIP_ONTIME" -> ratio(d.equipment(), e -> true, e -> {
                String st = EquipmentRules.state(e.getStatus(), e.getNextInspectDate(), today);
                return !EquipmentRules.OVERDUE.equals(st) && !EquipmentRules.NEVER.equals(st);
            });
            case "ACCIDENT" -> new Measure((double) d.events().stream().filter(e -> in(e.getOccurTime(), f, t)
                    && nz(e.getDead()) + nz(e.getSeriousInjured()) + nz(e.getMinorInjured()) > 0).count(), null, null);
            default -> Measure.NONE;
        };
    }

    YearData load(int year) {
        LocalDateTime from = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime to = from.plusYears(1);
        List<Hazard> hazards = hazardMapper.selectList(Wrappers.<Hazard>lambdaQuery()
                .ne(Hazard::getStatus, HazardStatus.REVOKED)
                .and(w -> w.ge(Hazard::getCreateTime, from).lt(Hazard::getCreateTime, to)
                        .or().ge(Hazard::getDeadline, from).lt(Hazard::getDeadline, to))
                .select(Hazard::getCreateTime, Hazard::getDeadline, Hazard::getRectifyTime, Hazard::getStatus));
        List<CheckTask> checks = checkTaskMapper.selectList(Wrappers.<CheckTask>lambdaQuery()
                .ge(CheckTask::getDueDate, from.toLocalDate()).lt(CheckTask::getDueDate, to.toLocalDate())
                .select(CheckTask::getDueDate, CheckTask::getStatus, CheckTask::getFinishTime));
        List<ShiftMeeting> meetings = meetingMapper.selectList(Wrappers.<ShiftMeeting>lambdaQuery()
                .ge(ShiftMeeting::getMeetingDate, from.toLocalDate()).lt(ShiftMeeting::getMeetingDate, to.toLocalDate())
                .select(ShiftMeeting::getTeamId, ShiftMeeting::getMeetingDate));
        // 班组的“开始日期”：建班组的日期；如果在那之前已经补录过班前会，就从第一次班前会算
        Map<Long, LocalDate> first = meetings.stream().collect(Collectors.toMap(ShiftMeeting::getTeamId, ShiftMeeting::getMeetingDate,
                (a, b) -> a.isBefore(b) ? a : b));
        Map<Long, LocalDate> teams = new HashMap<>();
        teamMapper.selectList(Wrappers.<MeetingTeam>lambdaQuery().eq(MeetingTeam::getStatus, 1).select(MeetingTeam::getId, MeetingTeam::getCreateTime))
                .forEach(tm -> {
                    LocalDate created = tm.getCreateTime() == null ? from.toLocalDate() : tm.getCreateTime().toLocalDate();
                    LocalDate f = first.get(tm.getId());
                    teams.put(tm.getId(), f != null && f.isBefore(created) ? f : created);
                });
        List<TrainExamRecord> exams = examRecordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                .eq(TrainExamRecord::getStatus, RecordStatus.SUBMITTED)
                .ge(TrainExamRecord::getSubmitTime, from).lt(TrainExamRecord::getSubmitTime, to)
                .select(TrainExamRecord::getSubmitTime, TrainExamRecord::getPassed));
        List<Long> books = bookMapper.selectList(Wrappers.<RespBook>lambdaQuery()
                        .eq(RespBook::getYear, year).ne(RespBook::getStatus, RespRules.DRAFT).select(RespBook::getId))
                .stream().map(RespBook::getId).toList();
        List<RespSign> signs = books.isEmpty() ? List.of() : signMapper.selectList(Wrappers.<RespSign>lambdaQuery()
                .in(RespSign::getBookId, books).select(RespSign::getStatus, RespSign::getSignTime, RespSign::getCreateTime));
        List<Equipment> equipment = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getStatus, EquipmentRules.IN_USE).select(Equipment::getStatus, Equipment::getNextInspectDate));
        List<EmergencyEvent> events = eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery()
                .ge(EmergencyEvent::getOccurTime, from).lt(EmergencyEvent::getOccurTime, to)
                .select(EmergencyEvent::getOccurTime, EmergencyEvent::getDead, EmergencyEvent::getSeriousInjured, EmergencyEvent::getMinorInjured));
        return new YearData(hazards, checks, teams, meetings, exams, signs, equipment, events);
    }

    private Map<String, KpiTarget> saved(int year) {
        return targetMapper.selectList(Wrappers.<KpiTarget>lambdaQuery().eq(KpiTarget::getYear, year))
                .stream().collect(Collectors.toMap(KpiTarget::getMetricCode, Function.identity(), (a, b) -> a));
    }

    private static <T> Measure ratio(List<T> rows, Predicate<T> counted, Predicate<T> ok) {
        long total = 0;
        long good = 0;
        for (T r : rows) {
            if (counted.test(r)) {
                total++;
                if (ok.test(r)) {
                    good++;
                }
            }
        }
        return Measure.ratio(good, total);
    }

    private static boolean in(LocalDateTime x, LocalDateTime from, LocalDateTime to) {
        return x != null && !x.isBefore(from) && x.isBefore(to);
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static int count(List<KpiVOs.Item> items, String light) {
        return (int) items.stream().filter(i -> light.equals(i.light())).count();
    }
}
