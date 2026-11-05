package com.safework.safety.accident;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.safety.emergency.EmergencyRules;
import com.safework.safety.entity.AccidentInvestigation;
import com.safework.safety.entity.AccidentMeasure;
import com.safework.safety.entity.AccidentPunish;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.mapper.AccidentInvestigationMapper;
import com.safework.safety.mapper.AccidentMeasureMapper;
import com.safework.safety.mapper.AccidentPunishMapper;
import com.safework.safety.mapper.EmergencyEventMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 事故调查处理（“四不放过”）：
 * <pre>
 * 事故快报（应急模块）──启动调查（指定组长、期限）──▶ 调查中
 *     ├─ 原因分析：直接原因、间接原因、管理原因、经济损失、教训
 *     ├─ 整改措施：指定责任人和期限 → 责任人填写落实情况（逾期提醒）
 *     ├─ 责任处理：责任人员、责任类别、处理措施
 *     └─ 警示教育：日期、参加人数、内容
 *          │ 四项都满足
 *          ▼
 *        结案（可选：事故案例自动写进 AI 知识库，以后问 AI 能查到）
 * </pre>
 * 有 accident:edit 的管理员和调查组长能填写；结案只有管理员能做。
 */
@Service
@RequiredArgsConstructor
public class AccidentService {

    public static final String PERM_LIST = "accident:list";
    public static final String PERM_EDIT = "accident:edit";
    public static final String LINK = "/safety/accident";
    public static final String BIZ_TYPE = "ACCIDENT";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AccidentInvestigationMapper invMapper;
    private final AccidentMeasureMapper measureMapper;
    private final AccidentPunishMapper punishMapper;
    private final EmergencyEventMapper eventMapper;
    private final UserService userService;
    private final MessageService messageService;
    private final ApplicationEventPublisher publisher;

    // ============================== 查询 ==============================

    public AccidentVOs.Overview overview() {
        LocalDate today = LocalDate.now();
        List<AccidentInvestigation> open = invMapper.selectList(Wrappers.<AccidentInvestigation>lambdaQuery()
                .eq(AccidentInvestigation::getStatus, AccidentRules.INVESTIGATING).select(AccidentInvestigation::getId, AccidentInvestigation::getDeadline));
        int overdue = (int) open.stream().filter(i -> today.isAfter(i.getDeadline())).count();
        List<AccidentMeasure> measures = measureMapper.selectList(Wrappers.<AccidentMeasure>lambdaQuery()
                .eq(AccidentMeasure::getStatus, AccidentRules.OPEN).select(AccidentMeasure::getDeadline));
        int closed = Math.toIntExact(invMapper.selectCount(Wrappers.<AccidentInvestigation>lambdaQuery()
                .eq(AccidentInvestigation::getStatus, AccidentRules.CLOSED)
                .ge(AccidentInvestigation::getCloseTime, LocalDate.of(today.getYear(), 1, 1).atStartOfDay())));
        return new AccidentVOs.Overview(open.size(), overdue, measures.size(),
                (int) measures.stream().filter(m -> today.isAfter(m.getDeadline())).count(), closed);
    }

    public AccidentVOs.Page page(AccidentDTOs.Query q) {
        LocalDate today = LocalDate.now();
        String kw = q.getKeyword();
        String st = q.getState();
        LambdaQueryWrapper<AccidentInvestigation> w = Wrappers.<AccidentInvestigation>lambdaQuery()
                .and(StringUtils.hasText(kw), x -> x.like(AccidentInvestigation::getEventTitle, kw).or().like(AccidentInvestigation::getEventNo, kw)
                        .or().like(AccidentInvestigation::getLeaderName, kw))
                .eq(AccidentRules.CLOSED.equals(st), AccidentInvestigation::getStatus, AccidentRules.CLOSED)
                .eq(AccidentRules.INVESTIGATING.equals(st) || AccidentRules.OVERDUE.equals(st), AccidentInvestigation::getStatus, AccidentRules.INVESTIGATING)
                .lt(AccidentRules.OVERDUE.equals(st), AccidentInvestigation::getDeadline, today)
                .orderByAsc(AccidentInvestigation::getStatus)
                .orderByDesc(AccidentInvestigation::getOccurTime);
        var page = invMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(q.getCurrent(), q.getSize()), w);
        List<AccidentInvestigation> list = page.getRecords();
        if (list.isEmpty()) {
            return new AccidentVOs.Page(List.of(), page.getTotal());
        }
        List<Long> ids = list.stream().map(AccidentInvestigation::getId).toList();
        Map<Long, List<AccidentMeasure>> measures = measureMapper.selectList(Wrappers.<AccidentMeasure>lambdaQuery()
                        .in(AccidentMeasure::getInvestigationId, ids)
                        .select(AccidentMeasure::getInvestigationId, AccidentMeasure::getStatus, AccidentMeasure::getDeadline))
                .stream().collect(Collectors.groupingBy(AccidentMeasure::getInvestigationId));
        Map<Long, Long> punishes = punishMapper.selectList(Wrappers.<AccidentPunish>lambdaQuery()
                        .in(AccidentPunish::getInvestigationId, ids).select(AccidentPunish::getInvestigationId))
                .stream().collect(Collectors.groupingBy(AccidentPunish::getInvestigationId, Collectors.counting()));
        List<AccidentVOs.Row> rows = list.stream().map(i -> {
            i.setState(AccidentRules.state(i.getStatus(), i.getDeadline(), today));
            List<AccidentMeasure> ms = measures.getOrDefault(i.getId(), List.of());
            int done = (int) ms.stream().filter(m -> AccidentRules.DONE.equals(m.getStatus())).count();
            int overdue = (int) ms.stream().filter(m -> AccidentRules.OVERDUE.equals(AccidentRules.measureState(m.getStatus(), m.getDeadline(), today))).count();
            int ok = (int) checks(i, punishes.getOrDefault(i.getId(), 0L).intValue(), ms.size(), done).stream().filter(AccidentRules.Check::ok).count();
            // 列表不需要长文字
            i.setDirectCause(null);
            i.setIndirectCause(null);
            i.setManagementCause(null);
            i.setLessons(null);
            i.setEducationNote(null);
            return new AccidentVOs.Row(i, ms.size(), done, overdue, ok);
        }).toList();
        return new AccidentVOs.Page(rows, page.getTotal());
    }

    /** 还没有启动调查的事故快报（误报直接结束、没有伤亡的也列出来，由管理员判断要不要调查） */
    public List<AccidentVOs.Candidate> candidates() {
        Set<Long> started = invMapper.selectList(Wrappers.<AccidentInvestigation>lambdaQuery().select(AccidentInvestigation::getEventId))
                .stream().map(AccidentInvestigation::getEventId).collect(Collectors.toSet());
        return eventMapper.selectList(Wrappers.<EmergencyEvent>lambdaQuery().orderByDesc(EmergencyEvent::getOccurTime).last("LIMIT 200"))
                .stream().filter(e -> !started.contains(e.getId()))
                .map(e -> new AccidentVOs.Candidate(e.getId(), e.getEventNo(), e.getTitle(), e.getLevel(), e.getAccidentType(), e.getOccurTime(),
                        e.getStatus(), nz(e.getDead()), nz(e.getSeriousInjured()), nz(e.getMinorInjured())))
                .toList();
    }

    public AccidentVOs.Detail detail(Long id) {
        AccidentInvestigation inv = load(id);
        LocalDate today = LocalDate.now();
        inv.setState(AccidentRules.state(inv.getStatus(), inv.getDeadline(), today));
        List<AccidentMeasure> measures = measureMapper.selectList(Wrappers.<AccidentMeasure>lambdaQuery()
                .eq(AccidentMeasure::getInvestigationId, id).orderByAsc(AccidentMeasure::getDeadline).orderByAsc(AccidentMeasure::getId));
        measures.forEach(m -> m.setState(AccidentRules.measureState(m.getStatus(), m.getDeadline(), today)));
        List<AccidentPunish> punishes = punishMapper.selectList(Wrappers.<AccidentPunish>lambdaQuery()
                .eq(AccidentPunish::getInvestigationId, id).orderByAsc(AccidentPunish::getId));
        int done = (int) measures.stream().filter(m -> AccidentRules.DONE.equals(m.getStatus())).count();
        boolean open = AccidentRules.INVESTIGATING.equals(inv.getStatus());
        return new AccidentVOs.Detail(inv, eventMapper.selectById(inv.getEventId()), measures, punishes,
                checks(inv, punishes.size(), measures.size(), done), open && canEdit(inv), open && StpUtil.hasPermission(PERM_EDIT));
    }

    /** 我负责的整改措施（不需要菜单权限，整改责任人可能是一线员工） */
    public List<AccidentMeasure> mine() {
        LocalDate today = LocalDate.now();
        List<AccidentMeasure> list = measureMapper.selectList(Wrappers.<AccidentMeasure>lambdaQuery()
                .eq(AccidentMeasure::getOwnerId, StpUtil.getLoginIdAsLong())
                .orderByAsc(AccidentMeasure::getStatus).orderByAsc(AccidentMeasure::getDeadline));
        if (list.isEmpty()) {
            return list;
        }
        Map<Long, String> titles = invMapper.selectBatchIds(list.stream().map(AccidentMeasure::getInvestigationId).distinct().toList())
                .stream().collect(Collectors.toMap(AccidentInvestigation::getId, AccidentInvestigation::getEventTitle));
        list.forEach(m -> {
            m.setState(AccidentRules.measureState(m.getStatus(), m.getDeadline(), today));
            m.setEventTitle(titles.get(m.getInvestigationId()));
        });
        return list;
    }

    /** 手机首页：我还没完成的整改措施数；没有菜单也没有措施的人返回 null（不显示入口） */
    public Integer myPending() {
        int n = Math.toIntExact(measureMapper.selectCount(Wrappers.<AccidentMeasure>lambdaQuery()
                .eq(AccidentMeasure::getOwnerId, StpUtil.getLoginIdAsLong()).eq(AccidentMeasure::getStatus, AccidentRules.OPEN)));
        return n > 0 || StpUtil.hasPermission(PERM_LIST) ? n : null;
    }

    // ============================== 调查组 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long start(AccidentDTOs.Team dto) {
        if (dto.getEventId() == null) {
            throw new BizException("请选择事故");
        }
        EmergencyEvent e = eventMapper.selectById(dto.getEventId());
        if (e == null) {
            throw new BizException(404, "事故快报不存在");
        }
        if (invMapper.selectCount(Wrappers.<AccidentInvestigation>lambdaQuery().eq(AccidentInvestigation::getEventId, e.getId())) > 0) {
            throw new BizException("这起事故已经启动调查了");
        }
        SysUser leader = activeUser(dto.getLeaderId(), "调查组长");
        LocalDate deadline = dto.getDeadline() != null ? dto.getDeadline()
                : AccidentRules.defaultDeadline(e.getLevel(), e.getOccurTime().toLocalDate());
        if (deadline.isBefore(LocalDate.now())) {
            // 补录很早以前的事故：按规定期限已经过了，给 7 天
            deadline = dto.getDeadline() == null ? LocalDate.now().plusDays(7) : deadline;
        }
        checkDeadline(deadline);
        AccidentInvestigation inv = new AccidentInvestigation();
        inv.setEventId(e.getId());
        inv.setEventNo(e.getEventNo());
        inv.setEventTitle(e.getTitle());
        inv.setAccidentType(e.getAccidentType());
        inv.setLevel(e.getLevel());
        inv.setOccurTime(e.getOccurTime());
        inv.setStatus(AccidentRules.INVESTIGATING);
        inv.setLeaderId(leader.getId());
        inv.setLeaderName(leader.getNickname());
        inv.setMembers(trim(dto.getMembers()));
        inv.setDeadline(deadline);
        inv.setStarterName(me().getNickname());
        inv.setToKnowledge(0);
        invMapper.insert(inv);
        notifyLeader(inv);
        return inv.getId();
    }

    /** 修改调查组长、成员、期限（管理员） */
    @Transactional(rollbackFor = Exception.class)
    public void updateTeam(Long id, AccidentDTOs.Team dto) {
        AccidentInvestigation inv = loadOpen(id);
        SysUser leader = activeUser(dto.getLeaderId(), "调查组长");
        LocalDate deadline = dto.getDeadline() == null ? inv.getDeadline() : dto.getDeadline();
        if (!deadline.equals(inv.getDeadline())) {
            checkDeadline(deadline);
        }
        boolean newLeader = !leader.getId().equals(inv.getLeaderId());
        inv.setLeaderId(leader.getId());
        inv.setLeaderName(leader.getNickname());
        inv.setMembers(trim(dto.getMembers()));
        inv.setDeadline(deadline);
        save(inv);
        if (newLeader) {
            notifyLeader(inv);
        }
    }

    // ============================== 调查内容（管理员或组长） ==============================

    @Transactional(rollbackFor = Exception.class)
    public void saveAnalysis(Long id, AccidentDTOs.Analysis dto) {
        AccidentInvestigation inv = loadEditable(id);
        inv.setDirectCause(trim(dto.getDirectCause()));
        inv.setIndirectCause(trim(dto.getIndirectCause()));
        inv.setManagementCause(trim(dto.getManagementCause()));
        inv.setEconomicLoss(dto.getEconomicLoss());
        inv.setLessons(trim(dto.getLessons()));
        save(inv);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveEducation(Long id, AccidentDTOs.Education dto) {
        AccidentInvestigation inv = loadEditable(id);
        if (dto.getEducationDate().isAfter(LocalDate.now())) {
            throw new BizException("教育日期不能晚于今天（开展了再登记）");
        }
        if (dto.getEducationDate().isBefore(inv.getOccurTime().toLocalDate())) {
            throw new BizException("教育日期不能早于事故发生日期");
        }
        inv.setEducationDate(dto.getEducationDate());
        inv.setEducationCount(dto.getEducationCount());
        inv.setEducationNote(trim(dto.getEducationNote()));
        save(inv);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addMeasure(Long id, AccidentDTOs.MeasureSave dto) {
        AccidentInvestigation inv = loadEditable(id);
        String error = AccidentRules.checkMeasureDeadline(dto.getDeadline(), LocalDate.now());
        if (error != null) {
            throw new BizException(error);
        }
        SysUser owner = activeUser(dto.getOwnerId(), "整改责任人");
        AccidentMeasure m = new AccidentMeasure();
        m.setInvestigationId(id);
        m.setContent(dto.getContent().trim());
        m.setOwnerId(owner.getId());
        m.setOwnerName(owner.getNickname());
        m.setDeadline(dto.getDeadline());
        m.setStatus(AccidentRules.OPEN);
        measureMapper.insert(m);
        messageService.send(List.of(owner.getId()), MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, id,
                "事故整改措施：请在 " + dto.getDeadline() + " 前完成",
                "《" + inv.getEventTitle() + "》：" + m.getContent(), LINK));
        return m.getId();
    }

    /** 删除措施：只能删还没完成的 */
    @Transactional(rollbackFor = Exception.class)
    public void removeMeasure(Long measureId) {
        AccidentMeasure m = loadMeasure(measureId);
        loadEditable(m.getInvestigationId());
        if (AccidentRules.DONE.equals(m.getStatus())) {
            throw new BizException("已经完成的措施不能删除");
        }
        measureMapper.deleteById(measureId);
    }

    /** 责任人（或管理员）填写落实情况 */
    @Transactional(rollbackFor = Exception.class)
    public void completeMeasure(Long measureId, String note) {
        AccidentMeasure m = loadMeasure(measureId);
        Long me = StpUtil.getLoginIdAsLong();
        if (!me.equals(m.getOwnerId()) && !StpUtil.hasPermission(PERM_EDIT)) {
            throw new BizException(403, "只有整改责任人能填写落实情况");
        }
        if (AccidentRules.DONE.equals(m.getStatus())) {
            throw new BizException("这项措施已经完成了");
        }
        AccidentInvestigation inv = load(m.getInvestigationId());
        if (!AccidentRules.INVESTIGATING.equals(inv.getStatus())) {
            throw new BizException("事故调查已经结案");
        }
        m.setStatus(AccidentRules.DONE);
        m.setDoneNote(note.trim());
        m.setDoneTime(LocalDateTime.now());
        m.setDoneBy(me().getNickname());
        if (measureMapper.updateById(m) == 0) {
            throw new BizException("这项措施刚被别人更新过，请刷新");
        }
        if (!me.equals(inv.getLeaderId())) {
            messageService.send(List.of(inv.getLeaderId()), MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, inv.getId(),
                    "整改措施已完成：" + m.getOwnerName(), "《" + inv.getEventTitle() + "》：" + m.getContent() + "。落实情况：" + m.getDoneNote(), LINK));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addPunish(Long id, AccidentDTOs.PunishSave dto) {
        loadEditable(id);
        if (!AccidentRules.DUTIES.containsKey(dto.getDuty())) {
            throw new BizException("责任类别不正确");
        }
        AccidentPunish p = new AccidentPunish();
        p.setInvestigationId(id);
        p.setPersonName(dto.getPersonName().trim());
        p.setDuty(dto.getDuty());
        p.setMeasure(dto.getMeasure().trim());
        p.setRecorderName(me().getNickname());
        punishMapper.insert(p);
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void removePunish(Long punishId) {
        AccidentPunish p = punishMapper.selectById(punishId);
        if (p == null) {
            throw new BizException(404, "处理记录不存在");
        }
        loadEditable(p.getInvestigationId());
        punishMapper.deleteById(punishId);
    }

    // ============================== 结案 ==============================

    /** 结案：“四不放过”都满足才行；可选把事故案例写进 AI 知识库（AI 模块在事务提交后处理） */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, boolean toKnowledge) {
        AccidentInvestigation inv = loadOpen(id);
        List<AccidentMeasure> measures = measureMapper.selectList(Wrappers.<AccidentMeasure>lambdaQuery()
                .eq(AccidentMeasure::getInvestigationId, id).orderByAsc(AccidentMeasure::getDeadline).orderByAsc(AccidentMeasure::getId));
        int punishes = Math.toIntExact(punishMapper.selectCount(Wrappers.<AccidentPunish>lambdaQuery().eq(AccidentPunish::getInvestigationId, id)));
        int done = (int) measures.stream().filter(m -> AccidentRules.DONE.equals(m.getStatus())).count();
        String error = AccidentRules.closeError(checks(inv, punishes, measures.size(), done));
        if (error != null) {
            throw new BizException(error);
        }
        inv.setStatus(AccidentRules.CLOSED);
        inv.setCloseTime(LocalDateTime.now());
        inv.setCloserName(me().getNickname());
        inv.setToKnowledge(toKnowledge ? 1 : 0);
        save(inv);
        if (toKnowledge) {
            EmergencyEvent e = eventMapper.selectById(inv.getEventId());
            String text = AccidentRules.caseText(inv.getEventNo(), inv.getEventTitle(),
                    EmergencyRules.LEVEL_LABELS.getOrDefault(inv.getLevel(), inv.getLevel()),
                    EmergencyRules.ACCIDENT_TYPES.getOrDefault(inv.getAccidentType(), inv.getAccidentType()),
                    inv.getOccurTime().format(TIME), e == null ? null : e.getLocation(), e == null ? null : e.getDescription(),
                    inv.getDirectCause(), inv.getIndirectCause(), inv.getManagementCause(),
                    measures.stream().map(AccidentMeasure::getContent).toList(), inv.getLessons());
            publisher.publishEvent(new AccidentVOs.ClosedEvent(inv.getId(), "事故案例：" + inv.getEventTitle(), text));
        }
    }

    // ============================== 定时任务 / 大屏用 ==============================

    public List<AccidentMeasure> openMeasures() {
        return measureMapper.selectList(Wrappers.<AccidentMeasure>lambdaQuery().eq(AccidentMeasure::getStatus, AccidentRules.OPEN));
    }

    public List<AccidentInvestigation> openInvestigations() {
        return invMapper.selectList(Wrappers.<AccidentInvestigation>lambdaQuery().eq(AccidentInvestigation::getStatus, AccidentRules.INVESTIGATING)
                .select(AccidentInvestigation::getId, AccidentInvestigation::getEventTitle, AccidentInvestigation::getDeadline, AccidentInvestigation::getLeaderId));
    }

    // ============================== 内部方法 ==============================

    private List<AccidentRules.Check> checks(AccidentInvestigation inv, int punishes, int measureTotal, int measureDone) {
        return AccidentRules.fourNots(inv.getDirectCause(), inv.getManagementCause(), punishes, measureTotal, measureDone,
                inv.getEducationDate(), inv.getEducationCount());
    }

    private boolean canEdit(AccidentInvestigation inv) {
        return StpUtil.hasPermission(PERM_EDIT) || Objects.equals(inv.getLeaderId(), StpUtil.getLoginIdAsLong());
    }

    private void notifyLeader(AccidentInvestigation inv) {
        messageService.send(List.of(inv.getLeaderId()), MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, inv.getId(),
                "你被指定为事故调查组长：" + inv.getEventTitle(),
                "请在 " + inv.getDeadline() + " 前查明原因、制定整改措施、处理责任人员、开展警示教育（四不放过）", LINK));
    }

    private void checkDeadline(LocalDate deadline) {
        LocalDate today = LocalDate.now();
        if (deadline.isBefore(today)) {
            throw new BizException("调查期限不能早于今天");
        }
        if (deadline.isAfter(today.plusDays(180))) {
            throw new BizException("调查期限最长 180 天");
        }
    }

    private SysUser activeUser(Long userId, String role) {
        SysUser u = userId == null ? null : userService.getById(userId);
        if (u == null) {
            throw new BizException("请选择" + role);
        }
        if (!Integer.valueOf(1).equals(u.getStatus())) {
            throw new BizException("【" + u.getNickname() + "】的账号已停用，不能做" + role);
        }
        return u;
    }

    private void save(AccidentInvestigation inv) {
        if (invMapper.updateById(inv) == 0) {
            throw new BizException("刚被别人修改过，请刷新后再试");
        }
    }

    private AccidentInvestigation load(Long id) {
        AccidentInvestigation inv = invMapper.selectById(id);
        if (inv == null) {
            throw new BizException(404, "事故调查不存在");
        }
        return inv;
    }

    private AccidentInvestigation loadOpen(Long id) {
        AccidentInvestigation inv = load(id);
        if (!AccidentRules.INVESTIGATING.equals(inv.getStatus())) {
            throw new BizException("事故调查已经结案，不能修改");
        }
        return inv;
    }

    private AccidentInvestigation loadEditable(Long id) {
        AccidentInvestigation inv = loadOpen(id);
        if (!canEdit(inv)) {
            throw new BizException(403, "只有调查组长和安全管理员能填写调查内容");
        }
        return inv;
    }

    private AccidentMeasure loadMeasure(Long id) {
        AccidentMeasure m = measureMapper.selectById(id);
        if (m == null) {
            throw new BizException(404, "整改措施不存在");
        }
        return m;
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
