package com.safework.safety.drill;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.emergency.EmergencyService;
import com.safework.safety.entity.DrillParticipant;
import com.safework.safety.entity.DrillProblem;
import com.safework.safety.entity.DrillScore;
import com.safework.safety.entity.EmergencyDrill;
import com.safework.safety.entity.EmergencyPlan;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.DrillParticipantMapper;
import com.safework.safety.mapper.DrillProblemMapper;
import com.safework.safety.mapper.DrillScoreMapper;
import com.safework.safety.mapper.EmergencyDrillMapper;
import com.safework.safety.mapper.EmergencyPlanMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.service.HazardService;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import com.safework.system.dto.UserOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 应急演练：安排（指定参演人员和角色）→ 参演人员手机签到 → 登记结果（确认实到人员）→ 评估打分、判断预案是否适宜 → 发现的问题转隐患整改。
 * 年度演练计划按预案类型检查频次（现场处置方案每年 2 次，其他 1 次）。
 */
@Service
@RequiredArgsConstructor
public class DrillService {

    public static final String PERM_LIST = "emergency:plan:list";
    public static final String PERM_EDIT = EmergencyService.PERM_PLAN_EDIT;
    public static final String BIZ_TYPE = "EMERGENCY_DRILL";
    public static final String TAG_PHOTO = "PHOTO";
    public static final String LINK = "/safety/emergency-plan?tab=drill";
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final EmergencyDrillMapper drillMapper;
    private final DrillParticipantMapper participantMapper;
    private final DrillScoreMapper scoreMapper;
    private final DrillProblemMapper problemMapper;
    private final EmergencyPlanMapper planMapper;
    private final HazardMapper hazardMapper;
    private final HazardService hazardService;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 查询 ==============================

    public List<DrillVOs.Row> list(DrillDTOs.Query q) {
        var w = Wrappers.<EmergencyDrill>lambdaQuery()
                .eq(q.getPlanId() != null, EmergencyDrill::getPlanId, q.getPlanId())
                .eq(StringUtils.hasText(q.getStatus()), EmergencyDrill::getStatus, q.getStatus());
        if (q.getYear() != null) {
            w.ge(EmergencyDrill::getPlanTime, LocalDate.of(q.getYear(), 1, 1).atStartOfDay())
                    .lt(EmergencyDrill::getPlanTime, LocalDate.of(q.getYear() + 1, 1, 1).atStartOfDay());
        }
        w.last("ORDER BY CASE status WHEN 'PLANNED' THEN 0 WHEN 'DONE' THEN 1 ELSE 2 END, plan_time DESC LIMIT 500");
        List<EmergencyDrill> drills = drillMapper.selectList(w);
        if (drills.isEmpty()) {
            return List.of();
        }
        List<Long> ids = drills.stream().map(EmergencyDrill::getId).toList();
        Map<Long, List<DrillParticipant>> ps = participantMapper.selectList(Wrappers.<DrillParticipant>lambdaQuery().in(DrillParticipant::getDrillId, ids))
                .stream().collect(Collectors.groupingBy(DrillParticipant::getDrillId));
        Map<Long, List<DrillProblem>> pbs = problemMapper.selectList(Wrappers.<DrillProblem>lambdaQuery().in(DrillProblem::getDrillId, ids))
                .stream().collect(Collectors.groupingBy(DrillProblem::getDrillId));
        return drills.stream().map(d -> {
            List<DrillParticipant> p = ps.getOrDefault(d.getId(), List.of());
            List<DrillProblem> pb = pbs.getOrDefault(d.getId(), List.of());
            return new DrillVOs.Row(d, p.size(), (int) p.stream().filter(x -> x.getSignTime() != null).count(),
                    pb.size(), (int) pb.stream().filter(x -> x.getHazardId() != null).count());
        }).toList();
    }

    public DrillVOs.Detail detail(Long id) {
        EmergencyDrill d = load(id);
        Long me = StpUtil.getLoginIdAsLong();
        List<DrillParticipant> ps = participants(id);
        DrillParticipant mine = ps.stream().filter(p -> p.getUserId().equals(me)).findFirst().orElse(null);
        boolean canList = StpUtil.hasPermission(PERM_LIST);
        if (!canList && mine == null) {
            throw new BizException(404, "演练不存在");
        }
        EmergencyPlan plan = planMapper.selectById(d.getPlanId());
        Map<String, DrillScore> saved = scoreMapper.selectList(Wrappers.<DrillScore>lambdaQuery().eq(DrillScore::getDrillId, id))
                .stream().collect(Collectors.toMap(DrillScore::getItemCode, Function.identity(), (a, b) -> a));
        List<DrillVOs.ScoreRow> scores = DrillRules.SCORE_ITEMS.stream().map(i -> {
            DrillScore s = saved.get(i.code());
            return new DrillVOs.ScoreRow(i.code(), i.name(), i.max(), i.hint(), s == null ? null : s.getScore(), s == null ? null : s.getRemark());
        }).toList();
        List<DrillProblem> problems = problemMapper.selectList(Wrappers.<DrillProblem>lambdaQuery().eq(DrillProblem::getDrillId, id).orderByAsc(DrillProblem::getId));
        List<String> actions = new ArrayList<>();
        if (StpUtil.hasPermission(PERM_EDIT)) {
            switch (d.getStatus()) {
                case DrillRules.PLANNED -> actions.addAll(List.of("EDIT", "CANCEL", "COMPLETE"));
                case DrillRules.DONE -> actions.add("EVALUATE");
                case DrillRules.EVALUATED -> {
                    if (problems.stream().anyMatch(p -> p.getHazardId() == null)) {
                        actions.add("TO_HAZARD");
                    }
                }
                default -> {
                }
            }
        }
        return new DrillVOs.Detail(d, plan == null ? null : plan.getPlanType(), plan == null ? null : plan.getPlanCode(), ps, scores,
                problemRows(problems), mine, mine != null && DrillRules.canSign(d.getStatus(), d.getPlanTime(), LocalDateTime.now()), actions);
    }

    /** 我的演练：最近 30 天和以后的 */
    public List<DrillVOs.Mine> mine() {
        Long me = StpUtil.getLoginIdAsLong();
        Map<Long, DrillParticipant> my = participantMapper.selectList(Wrappers.<DrillParticipant>lambdaQuery().eq(DrillParticipant::getUserId, me))
                .stream().collect(Collectors.toMap(DrillParticipant::getDrillId, Function.identity(), (a, b) -> a));
        if (my.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return drillMapper.selectList(Wrappers.<EmergencyDrill>lambdaQuery().in(EmergencyDrill::getId, my.keySet())
                        .ne(EmergencyDrill::getStatus, DrillRules.CANCELLED)
                        .ge(EmergencyDrill::getPlanTime, LocalDate.now().minusDays(30).atStartOfDay())
                        .orderByAsc(EmergencyDrill::getPlanTime))
                .stream().map(d -> {
                    DrillParticipant p = my.get(d.getId());
                    return new DrillVOs.Mine(d, DrillRules.ROLES.get(p.getRole()), p.getSignTime(), p.getAttended(),
                            DrillRules.canSign(d.getStatus(), d.getPlanTime(), now));
                }).toList();
    }

    /** 年度演练计划完成情况：每份启用的预案按类型要求的次数 */
    public DrillVOs.Annual annual(int year) {
        LocalDateTime from = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime to = from.plusYears(1);
        List<EmergencyPlan> plans = planMapper.selectList(Wrappers.<EmergencyPlan>lambdaQuery().eq(EmergencyPlan::getStatus, 1).orderByAsc(EmergencyPlan::getPlanCode));
        List<EmergencyDrill> drills = drillMapper.selectList(Wrappers.<EmergencyDrill>lambdaQuery()
                .ne(EmergencyDrill::getStatus, DrillRules.CANCELLED)
                .and(w -> w.ge(EmergencyDrill::getPlanTime, from).lt(EmergencyDrill::getPlanTime, to)
                        .or().ge(EmergencyDrill::getActualTime, from).lt(EmergencyDrill::getActualTime, to))
                .orderByAsc(EmergencyDrill::getPlanTime));
        Map<Long, List<EmergencyDrill>> byPlan = drills.stream().collect(Collectors.groupingBy(EmergencyDrill::getPlanId));
        LocalDateTime now = LocalDateTime.now();
        List<DrillVOs.YearRow> rows = new ArrayList<>();
        int doneOk = 0;
        int missing = 0;
        for (EmergencyPlan p : plans) {
            List<EmergencyDrill> l = byPlan.getOrDefault(p.getId(), List.of());
            int done = (int) l.stream().filter(d -> finished(d) && d.getActualTime() != null && !d.getActualTime().isBefore(from) && d.getActualTime().isBefore(to)).count();
            int planned = (int) l.stream().filter(d -> DrillRules.PLANNED.equals(d.getStatus()) && !d.getPlanTime().isBefore(now) && d.getPlanTime().isBefore(to)).count();
            String state = DrillRules.yearState(p.getPlanType(), done, planned);
            if (DrillRules.YEAR_DONE.equals(state)) {
                doneOk++;
            } else if (DrillRules.YEAR_MISSING.equals(state)) {
                missing++;
            }
            rows.add(new DrillVOs.YearRow(p.getId(), p.getPlanCode(), p.getTitle(), p.getPlanType(), DrillRules.requiredPerYear(p.getPlanType()),
                    done, planned, state, p.getLastDrillTime(), p.getReviseNote(),
                    l.stream().map(d -> new DrillVOs.YearDrill(d.getId(), d.getTitle(), d.getStatus(),
                            (d.getActualTime() != null ? d.getActualTime() : d.getPlanTime()).toLocalDate(), d.getGrade())).toList()));
        }
        return new DrillVOs.Annual(year, rows, plans.size(), doneOk, missing);
    }

    /** 概况：不依赖登录（大屏、智能体、定时任务都用） */
    public DrillVOs.Overview overview(LocalDate today) {
        DrillVOs.Annual a = annual(today.getYear());
        List<EmergencyDrill> planned = drillMapper.selectList(Wrappers.<EmergencyDrill>lambdaQuery().eq(EmergencyDrill::getStatus, DrillRules.PLANNED)
                .orderByAsc(EmergencyDrill::getPlanTime));
        List<EmergencyDrill> upcoming = planned.stream().filter(d -> !d.getPlanTime().toLocalDate().isBefore(today)
                && d.getPlanTime().toLocalDate().isBefore(today.plusDays(31))).toList();
        List<EmergencyDrill> recordLate = planned.stream().filter(d -> DrillRules.recordLate(d.getStatus(), d.getPlanTime(), today)).toList();
        List<EmergencyDrill> pending = drillMapper.selectList(Wrappers.<EmergencyDrill>lambdaQuery().eq(EmergencyDrill::getStatus, DrillRules.DONE)
                .orderByAsc(EmergencyDrill::getActualTime));
        List<EmergencyDrill> recent = drillMapper.selectList(Wrappers.<EmergencyDrill>lambdaQuery().eq(EmergencyDrill::getStatus, DrillRules.EVALUATED)
                .orderByDesc(EmergencyDrill::getEvaluateTime).last("LIMIT 5"));
        long open = problemMapper.selectCount(Wrappers.<DrillProblem>lambdaQuery().isNull(DrillProblem::getHazardId));
        int yearDone = a.rows().stream().mapToInt(DrillVOs.YearRow::done).sum();
        return new DrillVOs.Overview(a.year(), yearDone, a.missing(), upcoming, pending, recordLate, recent, (int) open);
    }

    /** 计划中的演练和参演人员（定时任务提醒用） */
    public Map<EmergencyDrill, List<Long>> plannedWithParticipants() {
        List<EmergencyDrill> planned = drillMapper.selectList(Wrappers.<EmergencyDrill>lambdaQuery().eq(EmergencyDrill::getStatus, DrillRules.PLANNED));
        Map<EmergencyDrill, List<Long>> m = new java.util.LinkedHashMap<>();
        for (EmergencyDrill d : planned) {
            m.put(d, participants(d.getId()).stream().map(DrillParticipant::getUserId).toList());
        }
        return m;
    }

    // ============================== 安排 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, DrillDTOs.Save dto) {
        if (!DrillRules.TYPES.contains(dto.getDrillType())) {
            throw new BizException("演练形式不正确");
        }
        EmergencyPlan plan = planMapper.selectById(dto.getPlanId());
        if (plan == null) {
            throw new BizException(404, "预案不存在");
        }
        EmergencyDrill d = id == null ? new EmergencyDrill() : load(id);
        if (id != null && !DrillRules.PLANNED.equals(d.getStatus())) {
            throw new BizException("只有计划中的演练可以修改");
        }
        if (id == null && !Integer.valueOf(1).equals(plan.getStatus())) {
            throw new BizException("预案已停用，不能安排演练");
        }
        if (id == null && dto.getPlanTime().toLocalDate().isBefore(LocalDate.now())) {
            throw new BizException("计划时间不能早于今天（已经演练过的，先安排再登记结果）");
        }
        List<DrillParticipant> ps = checkParticipants(dto.getParticipants());
        d.setPlanId(plan.getId());
        d.setPlanTitle(plan.getTitle());
        d.setTitle(dto.getTitle().trim());
        d.setDrillType(dto.getDrillType());
        d.setPlanTime(dto.getPlanTime());
        d.setLocation(trim(dto.getLocation()));
        d.setScenario(trim(dto.getScenario()));
        if (id == null) {
            d.setStatus(DrillRules.PLANNED);
            d.setOrganizerName(me().getNickname());
            drillMapper.insert(d);
        } else {
            drillMapper.updateById(d);
        }
        // 参演名单：保留原来的签到记录
        Map<Long, DrillParticipant> old = participants(d.getId()).stream().collect(Collectors.toMap(DrillParticipant::getUserId, Function.identity(), (a, b) -> a));
        participantMapper.delete(Wrappers.<DrillParticipant>lambdaQuery().eq(DrillParticipant::getDrillId, d.getId()));
        for (DrillParticipant p : ps) {
            DrillParticipant o = old.get(p.getUserId());
            p.setDrillId(d.getId());
            p.setSignTime(o == null ? null : o.getSignTime());
            participantMapper.insert(p);
        }
        Set<Long> to = new LinkedHashSet<>(EmergencyService.teamIds(plan));
        ps.forEach(p -> to.add(p.getUserId()));
        if (!to.isEmpty()) {
            messageService.send(to, MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, d.getId(),
                    (id == null ? "应急演练安排：" : "应急演练安排有变更：") + d.getTitle(),
                    "演练时间 " + d.getPlanTime().format(MDHM) + (d.getLocation() != null ? "，地点 " + d.getLocation() : "")
                            + "，演练预案《" + plan.getTitle() + "》" + (ps.isEmpty() ? "" : "。参演人员当天在手机“我的 → 应急演练”签到")
                            + "。请提前熟悉处置程序", LINK));
        }
        return d.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        EmergencyDrill d = load(id);
        if (!DrillRules.PLANNED.equals(d.getStatus())) {
            throw new BizException("只有计划中的演练可以取消");
        }
        d.setStatus(DrillRules.CANCELLED);
        drillMapper.updateById(d);
        List<Long> to = participants(id).stream().map(DrillParticipant::getUserId).toList();
        if (!to.isEmpty()) {
            messageService.send(to, MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, d.getId(), "应急演练取消：" + d.getTitle(),
                    "原定 " + d.getPlanTime().format(MDHM) + " 的演练已取消", LINK));
        }
    }

    // ============================== 签到、登记结果 ==============================

    @Transactional(rollbackFor = Exception.class)
    public LocalDateTime sign(Long id) {
        EmergencyDrill d = load(id);
        Long me = StpUtil.getLoginIdAsLong();
        DrillParticipant p = participants(id).stream().filter(x -> x.getUserId().equals(me)).findFirst()
                .orElseThrow(() -> new BizException("你不在这次演练的参演名单里"));
        if (p.getSignTime() != null) {
            return p.getSignTime();
        }
        if (!DrillRules.canSign(d.getStatus(), d.getPlanTime(), LocalDateTime.now())) {
            throw new BizException("签到时间是演练当天、开始前 " + DrillRules.SIGN_BEFORE_HOURS + " 小时起（演练时间 " + d.getPlanTime().format(MDHM) + "）");
        }
        p.setSignTime(LocalDateTime.now());
        participantMapper.updateById(p);
        return p.getSignTime();
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, DrillDTOs.Complete dto) {
        EmergencyDrill d = load(id);
        if (!DrillRules.PLANNED.equals(d.getStatus())) {
            throw new BizException("只有计划中的演练可以登记完成");
        }
        if (dto.getActualTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BizException("实际演练时间不能晚于现在");
        }
        List<DrillParticipant> ps = participants(id);
        Set<Long> attended;
        if (dto.getAttendedUserIds() != null) {
            attended = new HashSet<>(dto.getAttendedUserIds());
            Set<Long> inList = ps.stream().map(DrillParticipant::getUserId).collect(Collectors.toSet());
            if (!inList.containsAll(attended)) {
                throw new BizException("确认参加的人员不在参演名单里");
            }
        } else {
            attended = ps.stream().filter(p -> p.getSignTime() != null).map(DrillParticipant::getUserId).collect(Collectors.toSet());
        }
        if (dto.getParticipantCount() < attended.size()) {
            throw new BizException("参演人数不能少于名单里确认参加的 " + attended.size() + " 人");
        }
        for (DrillParticipant p : ps) {
            p.setAttended(attended.contains(p.getUserId()) ? 1 : 0);
            participantMapper.updateById(p);
        }
        d.setStatus(DrillRules.DONE);
        d.setActualTime(dto.getActualTime());
        d.setParticipantCount(dto.getParticipantCount());
        d.setSummary(dto.getSummary().trim());
        d.setProblems(trim(dto.getProblems()));
        d.setRecorderName(me().getNickname());
        drillMapper.updateById(d);
        EmergencyPlan plan = planMapper.selectById(d.getPlanId());
        if (plan != null && (plan.getLastDrillTime() == null || plan.getLastDrillTime().isBefore(dto.getActualTime()))) {
            plan.setLastDrillTime(dto.getActualTime());
            planMapper.updateById(plan);
        }
    }

    // ============================== 评估 ==============================

    @Transactional(rollbackFor = Exception.class)
    public DrillRules.Result evaluate(Long id, DrillDTOs.Evaluate dto) {
        EmergencyDrill d = load(id);
        if (!DrillRules.DONE.equals(d.getStatus())) {
            throw new BizException(DrillRules.EVALUATED.equals(d.getStatus()) ? "这次演练已经评估过了" : "先登记演练结果再评估");
        }
        List<DrillRules.Score> scores = dto.getScores().stream().map(s -> new DrillRules.Score(s.getCode(), s.getScore())).toList();
        String err = DrillRules.checkScores(scores);
        if (err != null) {
            throw new BizException(err);
        }
        boolean suitable = Boolean.TRUE.equals(dto.getPlanSuitable());
        if (!suitable && !StringUtils.hasText(dto.getReviseNote())) {
            throw new BizException("预案不适宜时要写修订意见");
        }
        List<DrillDTOs.ProblemIn> problems = dto.getProblems() == null ? List.of() : dto.getProblems();
        for (DrillDTOs.ProblemIn p : problems) {
            if (!DrillRules.PROBLEM_CATEGORIES.containsKey(p.getCategory())) {
                throw new BizException("问题类别不正确");
            }
        }
        DrillRules.Result r = DrillRules.result(scores);
        scoreMapper.delete(Wrappers.<DrillScore>lambdaQuery().eq(DrillScore::getDrillId, id));
        for (DrillDTOs.ScoreIn s : dto.getScores()) {
            DrillScore x = new DrillScore();
            x.setDrillId(id);
            x.setItemCode(s.getCode());
            x.setScore(s.getScore());
            x.setRemark(trim(s.getRemark()));
            scoreMapper.insert(x);
        }
        for (DrillDTOs.ProblemIn p : problems) {
            DrillProblem x = new DrillProblem();
            x.setDrillId(id);
            x.setCategory(p.getCategory());
            x.setDescription(p.getDescription().trim());
            problemMapper.insert(x);
        }
        SysUser me = me();
        d.setStatus(DrillRules.EVALUATED);
        d.setTotalScore(r.total());
        d.setGrade(r.grade());
        d.setPlanSuitable(suitable ? 1 : 0);
        d.setReviseNote(suitable ? null : dto.getReviseNote().trim());
        d.setEvaluatorName(me.getNickname());
        d.setEvaluateTime(LocalDateTime.now());
        drillMapper.updateById(d);
        if (!suitable) {
            EmergencyPlan plan = planMapper.selectById(d.getPlanId());
            if (plan != null) {
                plan.setReviseNote(d.getReviseNote());
                plan.setReviseTime(LocalDateTime.now());
                planMapper.updateById(plan);
                messageService.sendToPermission(PERM_EDIT, null, MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, d.getId(),
                        "预案需要修订：" + plan.getTitle(),
                        "“" + d.getTitle() + "”评估认为预案不适宜：" + d.getReviseNote() + "。修改预案后提示会自动消失", "/safety/emergency-plan"));
            }
        }
        return r;
    }

    /** 演练发现的问题转隐患整改（走隐患闭环） */
    @Transactional(rollbackFor = Exception.class)
    public Long toHazard(Long problemId) {
        DrillProblem p = problemMapper.selectById(problemId);
        if (p == null) {
            throw new BizException(404, "问题不存在");
        }
        if (p.getHazardId() != null) {
            throw new BizException("已经转过整改了：" + p.getHazardNo());
        }
        EmergencyDrill d = load(p.getDrillId());
        HazardReportDTO h = new HazardReportDTO();
        String title = "演练发现：" + p.getDescription();
        h.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
        h.setDescription(("“" + d.getTitle() + "”（" + (d.getActualTime() != null ? d.getActualTime().toLocalDate() : d.getPlanTime().toLocalDate())
                + "，预案《" + d.getPlanTitle() + "》）评估发现的问题，类别：" + DrillRules.PROBLEM_CATEGORIES.get(p.getCategory()) + "。\n" + p.getDescription()));
        h.setCategory(switch (p.getCategory()) {
            case "EQUIPMENT" -> "设备设施";
            case "TRAINING" -> "人员行为";
            default -> "安全管理";
        });
        h.setLevel("GENERAL");
        h.setLocation(d.getLocation() != null ? d.getLocation() : "应急演练");
        Long hazardId = hazardService.report(h);
        Hazard created = hazardMapper.selectById(hazardId);
        p.setHazardId(hazardId);
        p.setHazardNo(created.getHazardNo());
        problemMapper.updateById(p);
        return hazardId;
    }

    // ============================== 附件权限 ==============================

    public boolean canView(Long id) {
        if (drillMapper.selectById(id) == null) {
            return false;
        }
        return StpUtil.hasPermission(PERM_LIST) || participants(id).stream().anyMatch(p -> p.getUserId().equals(StpUtil.getLoginIdAsLong()));
    }

    public boolean canEditPhotos(Long id) {
        EmergencyDrill d = drillMapper.selectById(id);
        return d != null && !DrillRules.CANCELLED.equals(d.getStatus()) && StpUtil.hasPermission(PERM_EDIT);
    }

    // ============================== 内部 ==============================

    private List<DrillParticipant> checkParticipants(List<DrillDTOs.Participant> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        Map<Long, UserOptionVO> users = userService.options().stream().collect(Collectors.toMap(UserOptionVO::getId, Function.identity(), (a, b) -> a));
        Set<Long> seen = new HashSet<>();
        List<DrillParticipant> l = new ArrayList<>();
        for (DrillDTOs.Participant x : in) {
            if (!DrillRules.ROLES.containsKey(x.getRole())) {
                throw new BizException("参演角色不正确");
            }
            UserOptionVO u = users.get(x.getUserId());
            if (u == null) {
                throw new BizException("参演人员必须是本企业在职员工");
            }
            if (!seen.add(u.getId())) {
                throw new BizException("参演人员【" + u.getNickname() + "】重复了");
            }
            DrillParticipant p = new DrillParticipant();
            p.setUserId(u.getId());
            p.setUserName(u.getNickname());
            p.setDeptName(u.getDeptName());
            p.setRole(x.getRole());
            l.add(p);
        }
        return l;
    }

    private List<DrillVOs.ProblemRow> problemRows(List<DrillProblem> problems) {
        List<Long> hids = problems.stream().map(DrillProblem::getHazardId).filter(Objects::nonNull).toList();
        Map<Long, HazardStatus> st = hids.isEmpty() ? Map.of() : hazardMapper.selectBatchIds(hids).stream()
                .collect(Collectors.toMap(Hazard::getId, Hazard::getStatus, (a, b) -> a));
        return problems.stream().map(p -> {
            HazardStatus s = p.getHazardId() == null ? null : st.get(p.getHazardId());
            return new DrillVOs.ProblemRow(p, s == null ? null : s.name(), s == null ? null : s.getLabel());
        }).toList();
    }

    private List<DrillParticipant> participants(Long drillId) {
        return participantMapper.selectList(Wrappers.<DrillParticipant>lambdaQuery().eq(DrillParticipant::getDrillId, drillId).orderByAsc(DrillParticipant::getId));
    }

    private static boolean finished(EmergencyDrill d) {
        return DrillRules.DONE.equals(d.getStatus()) || DrillRules.EVALUATED.equals(d.getStatus());
    }

    private EmergencyDrill load(Long id) {
        EmergencyDrill d = drillMapper.selectById(id);
        if (d == null) {
            throw new BizException(404, "演练不存在");
        }
        return d;
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
