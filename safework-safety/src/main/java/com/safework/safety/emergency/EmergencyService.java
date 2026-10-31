package com.safework.safety.emergency;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.entity.EmergencyDrill;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.EmergencyEventLog;
import com.safework.safety.entity.EmergencyPlan;
import com.safework.safety.mapper.EmergencyDrillMapper;
import com.safework.safety.mapper.EmergencyEventLogMapper;
import com.safework.safety.mapper.EmergencyEventMapper;
import com.safework.safety.mapper.EmergencyPlanMapper;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.safety.service.SpaceService;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应急管理：预案库、事故快报与应急响应、应急演练。
 * <pre>
 * 事故快报 ──▶ 通知安全员（实时推送）
 *    │
 *    ▼ 安全员选择预案“启动应急响应”
 * 响应中 ──▶ 通知预案的应急小组 + 安全员；处置过程随时追加记录（时间线）
 *    │
 *    ▼ 结束（必须写事后总结）
 * 已结束
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class EmergencyService {

    public static final String PERM_HANDLE = "emergency:event:handle";
    public static final String PERM_PLAN_EDIT = "emergency:plan:edit";
    static final String BIZ_TYPE = "EMERGENCY";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final EmergencyPlanMapper planMapper;
    private final EmergencyEventMapper eventMapper;
    private final EmergencyEventLogMapper logMapper;
    private final EmergencyDrillMapper drillMapper;
    private final SerialNoGenerator serialNo;
    private final SpaceService spaceService;
    private final UserService userService;
    private final MessageService messageService;

    // ============================== 统计 ==============================

    public EmergencyOverviewVO overview() {
        long responding = eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery().eq(EmergencyEvent::getStatus, EmergencyRules.RESPONDING));
        long thisYear = eventMapper.selectCount(Wrappers.<EmergencyEvent>lambdaQuery()
                .ge(EmergencyEvent::getOccurTime, LocalDate.now().withDayOfYear(1).atStartOfDay()));
        List<EmergencyPlan> plans = enabledPlans();
        long warnings = plans.stream().filter(p -> EmergencyRules.DRILL_OVERDUE.equals(p.getDrillState())
                || EmergencyRules.DRILL_NEVER.equals(p.getDrillState())).count();
        return new EmergencyOverviewVO(responding, thisYear, plans.size(), warnings);
    }

    // ============================== 预案 ==============================

    public List<EmergencyPlan> plans(String keyword) {
        List<EmergencyPlan> list = planMapper.selectList(Wrappers.<EmergencyPlan>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w.like(EmergencyPlan::getTitle, keyword).or()
                        .like(EmergencyPlan::getPlanCode, keyword).or().like(EmergencyPlan::getScope, keyword))
                .orderByDesc(EmergencyPlan::getStatus)
                .orderByAsc(EmergencyPlan::getPlanCode));
        list.forEach(this::markDrill);
        return list;
    }

    /** 启用的预案（启动响应、AI 匹配用） */
    public List<EmergencyPlan> enabledPlans() {
        List<EmergencyPlan> list = planMapper.selectList(Wrappers.<EmergencyPlan>lambdaQuery()
                .eq(EmergencyPlan::getStatus, 1).orderByAsc(EmergencyPlan::getPlanCode));
        list.forEach(this::markDrill);
        return list;
    }

    public EmergencyPlan plan(Long id) {
        EmergencyPlan p = planMapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "预案不存在");
        }
        markDrill(p);
        return p;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long savePlan(Long id, PlanSaveDTO dto) {
        checkDict(dto.getPlanType(), EmergencyRules.PLAN_TYPES.keySet(), "预案类型");
        checkDict(dto.getAccidentType(), EmergencyRules.ACCIDENT_TYPES.keySet(), "事故类型");
        String code = dto.getPlanCode().trim();
        EmergencyPlan same = planMapper.selectOne(Wrappers.<EmergencyPlan>lambdaQuery().eq(EmergencyPlan::getPlanCode, code));
        if (same != null && !same.getId().equals(id)) {
            throw new BizException("预案编号【" + code + "】已存在");
        }
        EmergencyPlan p = id == null ? new EmergencyPlan() : plan(id);
        p.setPlanCode(code);
        p.setTitle(dto.getTitle().trim());
        p.setPlanType(dto.getPlanType());
        p.setAccidentType(dto.getAccidentType());
        p.setScope(dto.getScope());
        p.setContent(dto.getContent().trim());
        List<SysUser> team = activeUsers(dto.getTeamUserIds());
        p.setTeamUserIds(team.stream().map(u -> String.valueOf(u.getId())).collect(Collectors.joining(",")));
        p.setTeamNames(team.stream().map(SysUser::getNickname).collect(Collectors.joining("、")));
        p.setUpdaterName(currentUser().getNickname());
        if (id == null) {
            p.setStatus(1);
            planMapper.insert(p);
        } else {
            planMapper.updateById(p);
        }
        return p.getId();
    }

    public void changePlanStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException("状态只能是 0 或 1");
        }
        EmergencyPlan p = plan(id);
        p.setStatus(status);
        planMapper.updateById(p);
    }

    // ============================== 事件 ==============================

    public Page<EmergencyEvent> events(EventQueryDTO q) {
        String kw = q.getKeyword();
        return eventMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<EmergencyEvent>lambdaQuery()
                .and(StringUtils.hasText(kw), w -> w.like(EmergencyEvent::getEventNo, kw).or()
                        .like(EmergencyEvent::getTitle, kw).or().like(EmergencyEvent::getLocation, kw))
                .eq(StringUtils.hasText(q.getStatus()), EmergencyEvent::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getAccidentType()), EmergencyEvent::getAccidentType, q.getAccidentType())
                // 响应中的排在最前面
                .last("ORDER BY CASE status WHEN 'RESPONDING' THEN 0 WHEN 'REPORTED' THEN 1 ELSE 2 END, occur_time DESC"));
    }

    public EventDetailVO detail(Long id) {
        EmergencyEvent e = loadEvent(id);
        List<EmergencyEventLog> logs = logMapper.selectList(Wrappers.<EmergencyEventLog>lambdaQuery()
                .eq(EmergencyEventLog::getEventId, id)
                .orderByAsc(EmergencyEventLog::getCreateTime)
                .orderByAsc(EmergencyEventLog::getId));
        EmergencyPlan plan = e.getPlanId() == null ? null : planMapper.selectById(e.getPlanId());
        List<String> team = plan == null || !StringUtils.hasText(plan.getTeamNames()) ? List.of()
                : Arrays.asList(plan.getTeamNames().split("、"));
        List<String> actions = new ArrayList<>();
        if (StpUtil.hasPermission(PERM_HANDLE)) {
            for (EmergencyRules.Action a : EmergencyRules.Action.values()) {
                if (a.canFire(e.getStatus())) {
                    actions.add(a.name());
                }
            }
        }
        return new EventDetailVO(e, logs, actions, plan, team);
    }

    /** 事故快报：任何员工都可以报，先报告再完善 */
    @Transactional(rollbackFor = Exception.class)
    public Long report(EventReportDTO dto) {
        checkDict(dto.getAccidentType(), EmergencyRules.ACCIDENT_TYPES.keySet(), "事故类型");
        checkDict(dto.getLevel(), EmergencyRules.LEVELS, "事故等级");
        if (dto.getOccurTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BizException("事发时间不能晚于现在");
        }
        int dead = nz(dto.getDead());
        int serious = nz(dto.getSeriousInjured());
        SysUser me = currentUser();
        EmergencyEvent e = new EmergencyEvent();
        e.setEventNo(serialNo.next("YJ"));
        e.setTitle(dto.getTitle().trim());
        e.setAccidentType(dto.getAccidentType());
        // 伤亡人数决定最低等级，报低了自动调高
        e.setLevel(EmergencyRules.effectiveLevel(dto.getLevel(), dead, serious));
        e.setLocation(dto.getLocation().trim());
        if (dto.getSpaceId() != null) {
            ConfinedSpace s = spaceService.getById(dto.getSpaceId());
            e.setSpaceId(s.getId());
            e.setSpaceName(s.getSpaceName());
        }
        e.setDescription(dto.getDescription());
        e.setDead(dead);
        e.setSeriousInjured(serious);
        e.setMinorInjured(nz(dto.getMinorInjured()));
        e.setOccurTime(dto.getOccurTime());
        e.setReporterId(me.getId());
        e.setReporterName(me.getNickname());
        e.setStatus(EmergencyRules.REPORTED);
        eventMapper.insert(e);
        String adjusted = e.getLevel().equals(dto.getLevel()) ? ""
                : "（伤亡人数达到" + EmergencyRules.LEVEL_LABELS.get(e.getLevel()) + "标准，等级已自动调高）";
        writeLog(e, "REPORT", casualties(e) + adjusted, me);
        messageService.sendToPermission(PERM_HANDLE, me.getId(), draft(e, MessageCategory.WARN,
                "【事故快报】" + EmergencyRules.LEVEL_LABELS.get(e.getLevel()) + "：" + e.getTitle(),
                me.getNickname() + " 报告：" + e.getLocation() + " 发生" + EmergencyRules.ACCIDENT_TYPES.get(e.getAccidentType())
                        + "，" + casualties(e) + "。请立即确认并启动应急响应"));
        return e.getId();
    }

    /** 启动应急响应：选定预案，通知应急小组和安全员 */
    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, EventActionDTO dto) {
        if (dto.getPlanId() == null) {
            throw new BizException("请选择要启动的应急预案");
        }
        SysUser me = currentUser();
        EmergencyEvent e = loadEvent(id);
        check(e, EmergencyRules.Action.START);
        EmergencyPlan plan = plan(dto.getPlanId());
        if (!Objects.equals(plan.getStatus(), 1)) {
            throw new BizException("预案【" + plan.getTitle() + "】已停用");
        }
        e.setPlanId(plan.getId());
        e.setPlanTitle(plan.getTitle());
        e.setCommanderId(me.getId());
        e.setCommanderName(me.getNickname());
        e.setResponseTime(LocalDateTime.now());
        e.setStatus(EmergencyRules.Action.START.to);
        update(e);
        writeLog(e, "START", "启动《" + plan.getTitle() + "》，现场指挥：" + me.getNickname()
                + (StringUtils.hasText(dto.getContent()) ? "。" + dto.getContent().trim() : ""), me);
        Set<Long> receivers = new LinkedHashSet<>(teamIds(plan));
        receivers.addAll(messageService.listPermissionUsers(PERM_HANDLE));
        receivers.remove(me.getId());
        messageService.send(receivers, draft(e, MessageCategory.WARN,
                "【应急响应】" + e.getTitle(),
                "已启动《" + plan.getTitle() + "》，现场指挥 " + me.getNickname() + "，地点 " + e.getLocation()
                        + "。应急小组成员请立即按预案到位"));
    }

    /** 处置过程中追加记录（到达现场、人员救出、泄漏点封堵……） */
    @Transactional(rollbackFor = Exception.class)
    public void addLog(Long id, EventActionDTO dto) {
        if (!StringUtils.hasText(dto.getContent())) {
            throw new BizException("请填写处置情况");
        }
        SysUser me = currentUser();
        EmergencyEvent e = loadEvent(id);
        check(e, EmergencyRules.Action.UPDATE);
        writeLog(e, "UPDATE", dto.getContent().trim(), me);
    }

    /** 结束：必须写事后总结；误报可以不启动响应直接结束 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, EventActionDTO dto) {
        if (!StringUtils.hasText(dto.getContent())) {
            throw new BizException("请填写事后总结（处置经过、原因初步分析、整改措施；误报请说明情况）");
        }
        SysUser me = currentUser();
        EmergencyEvent e = loadEvent(id);
        check(e, EmergencyRules.Action.CLOSE);
        boolean responded = EmergencyRules.RESPONDING.equals(e.getStatus());
        e.setSummary(dto.getContent().trim());
        e.setCloseTime(LocalDateTime.now());
        e.setStatus(EmergencyRules.Action.CLOSE.to);
        update(e);
        writeLog(e, "CLOSE", dto.getContent().trim(), me);
        if (responded) {
            Set<Long> receivers = new LinkedHashSet<>(e.getPlanId() == null ? List.of() : teamIds(planMapper.selectById(e.getPlanId())));
            receivers.addAll(messageService.listPermissionUsers(PERM_HANDLE));
            receivers.add(e.getReporterId());
            receivers.remove(me.getId());
            messageService.send(receivers, draft(e, MessageCategory.NOTICE, "应急响应结束：" + e.getTitle(),
                    me.getNickname() + " 结束了应急响应。" + abbreviate(e.getSummary(), 80)));
        }
    }

    // ============================== 演练 ==============================

    public List<EmergencyDrill> drills(Long planId) {
        return drillMapper.selectList(Wrappers.<EmergencyDrill>lambdaQuery()
                .eq(planId != null, EmergencyDrill::getPlanId, planId)
                .last("ORDER BY CASE status WHEN 'PLANNED' THEN 0 ELSE 1 END, plan_time DESC"));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createDrill(DrillSaveDTO dto) {
        checkDict(dto.getDrillType(), Set.of("TABLETOP", "LIVE"), "演练形式");
        EmergencyPlan plan = plan(dto.getPlanId());
        EmergencyDrill d = new EmergencyDrill();
        d.setPlanId(plan.getId());
        d.setPlanTitle(plan.getTitle());
        d.setTitle(dto.getTitle().trim());
        d.setDrillType(dto.getDrillType());
        d.setPlanTime(dto.getPlanTime());
        d.setLocation(dto.getLocation());
        d.setStatus("PLANNED");
        drillMapper.insert(d);
        List<Long> team = teamIds(plan);
        if (!team.isEmpty()) {
            messageService.send(team, MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE + "_DRILL", d.getId(),
                    "应急演练安排：" + d.getTitle(),
                    "演练时间 " + d.getPlanTime().format(HM) + (StringUtils.hasText(d.getLocation()) ? "，地点 " + d.getLocation() : "")
                            + "，演练预案《" + plan.getTitle() + "》。请提前熟悉处置程序", "/safety/emergency-plan?tab=drill"));
        }
        return d.getId();
    }

    /** 完成演练：记录总结，更新预案的“最近演练时间” */
    @Transactional(rollbackFor = Exception.class)
    public void completeDrill(Long id, DrillCompleteDTO dto) {
        EmergencyDrill d = loadDrill(id);
        if (!"PLANNED".equals(d.getStatus())) {
            throw new BizException("只有计划中的演练可以登记完成");
        }
        if (dto.getActualTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new BizException("实际演练时间不能晚于现在");
        }
        d.setStatus("DONE");
        d.setActualTime(dto.getActualTime());
        d.setParticipantCount(dto.getParticipantCount());
        d.setSummary(dto.getSummary().trim());
        d.setProblems(dto.getProblems());
        d.setRecorderName(currentUser().getNickname());
        drillMapper.updateById(d);
        EmergencyPlan plan = planMapper.selectById(d.getPlanId());
        if (plan != null && (plan.getLastDrillTime() == null || plan.getLastDrillTime().isBefore(dto.getActualTime()))) {
            plan.setLastDrillTime(dto.getActualTime());
            planMapper.updateById(plan);
        }
    }

    public void cancelDrill(Long id) {
        EmergencyDrill d = loadDrill(id);
        if (!"PLANNED".equals(d.getStatus())) {
            throw new BizException("只有计划中的演练可以取消");
        }
        d.setStatus("CANCELLED");
        drillMapper.updateById(d);
    }

    // ============================== 内部方法 ==============================

    private void markDrill(EmergencyPlan p) {
        EmergencyRules.DrillState s = EmergencyRules.drillState(p.getPlanType(), p.getLastDrillTime(), LocalDate.now());
        p.setDrillState(s.state());
        p.setNextDrillDue(s.nextDue());
    }

    private EmergencyEvent loadEvent(Long id) {
        EmergencyEvent e = eventMapper.selectById(id);
        if (e == null) {
            throw new BizException(404, "事件不存在");
        }
        return e;
    }

    private EmergencyDrill loadDrill(Long id) {
        EmergencyDrill d = drillMapper.selectById(id);
        if (d == null) {
            throw new BizException(404, "演练不存在");
        }
        return d;
    }

    private static void check(EmergencyEvent e, EmergencyRules.Action a) {
        if (!a.canFire(e.getStatus())) {
            throw new BizException("事件当前状态不能执行【" + a.label + "】");
        }
    }

    private void update(EmergencyEvent e) {
        if (eventMapper.updateById(e) == 0) {
            throw new BizException(409, "事件已被他人处理，请刷新后重试");
        }
    }

    private void writeLog(EmergencyEvent e, String action, String content, SysUser me) {
        EmergencyEventLog log = new EmergencyEventLog();
        log.setEventId(e.getId());
        log.setAction(action);
        log.setContent(content);
        log.setOperatorId(me.getId());
        log.setOperatorName(me.getNickname());
        logMapper.insert(log);
    }

    private List<SysUser> activeUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<SysUser> result = new ArrayList<>();
        for (Long uid : new LinkedHashSet<>(ids)) {
            SysUser u = uid == null ? null : userService.getById(uid);
            if (u == null) {
                throw new BizException("应急小组成员不存在");
            }
            if (!Integer.valueOf(1).equals(u.getStatus())) {
                throw new BizException("应急小组成员【" + u.getNickname() + "】的账号已停用");
            }
            result.add(u);
        }
        if (result.size() > 30) {
            throw new BizException("应急小组成员不超过 30 人");
        }
        return result;
    }

    static List<Long> teamIds(EmergencyPlan plan) {
        if (plan == null || !StringUtils.hasText(plan.getTeamUserIds())) {
            return List.of();
        }
        return Arrays.stream(plan.getTeamUserIds().split(",")).map(String::trim).filter(StringUtils::hasText)
                .map(Long::valueOf).toList();
    }

    private static String casualties(EmergencyEvent e) {
        if (e.getDead() == 0 && e.getSeriousInjured() == 0 && e.getMinorInjured() == 0) {
            return "暂无人员伤亡";
        }
        return "死亡 " + e.getDead() + " 人、重伤 " + e.getSeriousInjured() + " 人、轻伤 " + e.getMinorInjured() + " 人";
    }

    private static MessageDraft draft(EmergencyEvent e, String category, String title, String content) {
        return MessageDraft.of(category, BIZ_TYPE, e.getId(), title, content, "/safety/emergency?detail=" + e.getId());
    }

    private static void checkDict(String value, java.util.Collection<String> allowed, String name) {
        if (value == null || !allowed.contains(value)) {
            throw new BizException(name + "不正确");
        }
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static String abbreviate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private SysUser currentUser() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }
}
