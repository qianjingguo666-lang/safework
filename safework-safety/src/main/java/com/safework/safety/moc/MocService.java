package com.safework.safety.moc;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.MocChange;
import com.safework.safety.entity.MocLog;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.mapper.MocChangeMapper;
import com.safework.safety.mapper.MocLogMapper;
import com.safework.safety.service.LecEvaluator;
import com.safework.safety.service.SerialNoGenerator;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 变更管理（MOC）：工艺、设备、原辅料、关键人员、管理制度的变更，先评估风险、审批，实施后做投用前安全检查（PSSR）才能投用。
 * 临时变更到期要恢复原状。
 * <ul>
 *   <li>moc:list 能看、能申请（申请人管自己的草稿、实施、恢复）</li>
 *   <li>moc:edit 安全管理：风险评估、退回、投用前检查</li>
 *   <li>moc:approve 审批：不能审批自己的申请；较大、重大风险要两个人审批</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MocService {

    public static final String PERM_LIST = "moc:list";
    public static final String PERM_EDIT = "moc:edit";
    public static final String PERM_APPROVE = "moc:approve";
    public static final String BIZ_TYPE = "MOC";
    public static final String LINK = "/safety/moc";
    public static final String EDIT = "EDIT";

    private static final TypeReference<List<MocVOs.PssrItem>> PSSR_TYPE = new TypeReference<>() {
    };

    private final MocChangeMapper changeMapper;
    private final MocLogMapper logMapper;
    private final SerialNoGenerator serialNo;
    private final UserService userService;
    private final MessageService messageService;
    private final ObjectMapper json = new ObjectMapper();

    // ============================== 查询 ==============================

    public MocVOs.Overview overview() {
        List<MocChange> open = changeMapper.selectList(Wrappers.<MocChange>lambdaQuery()
                .notIn(MocChange::getStatus, MocRules.CLOSED, MocRules.CANCELLED, MocRules.DRAFT));
        List<MocChange> temp = temporaryOpen();
        LocalDate today = LocalDate.now();
        int closed = Math.toIntExact(changeMapper.selectCount(Wrappers.<MocChange>lambdaQuery()
                .eq(MocChange::getStatus, MocRules.CLOSED).ge(MocChange::getCloseTime, LocalDate.of(today.getYear(), 1, 1).atStartOfDay())));
        return new MocVOs.Overview(count(open, MocRules.ASSESSING), count(open, MocRules.APPROVING), count(open, MocRules.IMPLEMENTING),
                count(open, MocRules.PSSR), temp.size(), (int) temp.stream().filter(c -> c.getRestoreDate().isBefore(today)).count(),
                closed, todoIds().size());
    }

    public MocVOs.Page page(MocDTOs.Query q) {
        String kw = q.getKeyword();
        LambdaQueryWrapper<MocChange> w = Wrappers.<MocChange>lambdaQuery()
                .and(StringUtils.hasText(kw), x -> x.like(MocChange::getTitle, kw).or().like(MocChange::getChangeNo, kw)
                        .or().like(MocChange::getApplicantName, kw).or().like(MocChange::getLocation, kw))
                .eq(StringUtils.hasText(q.getStatus()), MocChange::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getChangeType()), MocChange::getChangeType, q.getChangeType())
                // 草稿只有申请人自己看得到
                .and(x -> x.ne(MocChange::getStatus, MocRules.DRAFT).or().eq(MocChange::getApplicantId, StpUtil.getLoginIdAsLong()))
                .orderByDesc(MocChange::getCreateTime);
        Set<Long> todo = todoIds();
        if (Boolean.TRUE.equals(q.getTodo())) {
            if (todo.isEmpty()) {
                return new MocVOs.Page(List.of(), 0);
            }
            w.in(MocChange::getId, todo);
        }
        var page = changeMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(q.getCurrent(), q.getSize()), w);
        Map<Long, Integer> approved = approvedCounts(page.getRecords());
        LocalDate today = LocalDate.now();
        List<MocVOs.Row> rows = page.getRecords().stream().map(c -> {
            c.setPssr(null);
            return new MocVOs.Row(c, approved.getOrDefault(c.getId(), 0), todo.contains(c.getId()), restoreOverdue(c, today));
        }).toList();
        return new MocVOs.Page(rows, page.getTotal());
    }

    public MocVOs.Detail detail(Long id) {
        MocChange c = load(id);
        if (MocRules.DRAFT.equals(c.getStatus()) && !isApplicant(c) && !StpUtil.hasPermission(PERM_EDIT)) {
            throw new BizException(404, "变更不存在");
        }
        List<MocLog> logs = logMapper.selectList(Wrappers.<MocLog>lambdaQuery().eq(MocLog::getChangeId, id).orderByAsc(MocLog::getCreateTime).orderByAsc(MocLog::getId));
        int approved = approvedThisRound(c, logs).size();
        List<MocVOs.PssrItem> pssr = c.getPssr() == null ? MocRules.PSSR_ITEMS.stream().map(i -> new MocVOs.PssrItem(i, null, null)).toList() : read(c.getPssr());
        c.setPssr(null);
        return new MocVOs.Detail(c, pssr, logs, approved, actions(c, logs));
    }

    /** 手机首页：要我处理的变更数；没有菜单返回 null */
    public Integer myTodo() {
        if (!StpUtil.hasPermission(PERM_LIST)) {
            return null;
        }
        return todoIds().size();
    }

    // ============================== 申请人 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long create(MocDTOs.Save dto) {
        check(dto);
        SysUser me = me();
        MocChange c = new MocChange();
        fill(c, dto);
        c.setChangeNo(serialNo.next("BG"));
        c.setApplicantId(me.getId());
        c.setApplicantName(me.getNickname());
        c.setStatus(MocRules.DRAFT);
        c.setApprovalRound(1);
        c.setApprovalsNeeded(1);
        c.setRestored(0);
        changeMapper.insert(c);
        log(c, "CREATE", "新建变更申请");
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MocDTOs.Save dto) {
        MocChange c = load(id);
        requireApplicant(c);
        if (!MocRules.DRAFT.equals(c.getStatus())) {
            throw new BizException("只有草稿能修改（被退回的会回到草稿）");
        }
        check(dto);
        fill(c, dto);
        save(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        MocChange c = load(id);
        requireApplicant(c);
        fire(c, MocRules.Action.SUBMIT, "提交风险评估");
        notifyPerm(PERM_EDIT, c, MessageCategory.TODO, "变更待风险评估：" + c.getTitle(), c.getApplicantName() + " 提交了变更申请，请进行风险评估");
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String comment) {
        MocChange c = load(id);
        if (!isApplicant(c) && !StpUtil.hasPermission(PERM_EDIT)) {
            throw new BizException(403, "只有申请人或安全管理员能取消");
        }
        fire(c, MocRules.Action.CANCEL, StringUtils.hasText(comment) ? comment.trim() : "取消变更");
    }

    /** 实施完成，进入投用前安全检查 */
    @Transactional(rollbackFor = Exception.class)
    public void implemented(Long id, String note) {
        MocChange c = load(id);
        requireApplicant(c);
        if (!StringUtils.hasText(note)) {
            throw new BizException("请写明实施情况");
        }
        c.setImplementNote(note.trim());
        c.setImplementTime(LocalDateTime.now());
        fire(c, MocRules.Action.IMPLEMENTED, "实施完成：" + note.trim());
        notifyPerm(PERM_EDIT, c, MessageCategory.TODO, "变更待投用前安全检查：" + c.getTitle(), "实施情况：" + c.getImplementNote());
    }

    /** 临时变更恢复原状 */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long id, String note) {
        MocChange c = load(id);
        if (!isApplicant(c) && !StpUtil.hasPermission(PERM_EDIT)) {
            throw new BizException(403, "只有申请人或安全管理员能登记恢复");
        }
        if (!Integer.valueOf(1).equals(c.getTemporary()) || Integer.valueOf(1).equals(c.getRestored())) {
            throw new BizException("只有还没恢复的临时变更才需要登记恢复");
        }
        if (!StringUtils.hasText(note)) {
            throw new BizException("请写明恢复情况");
        }
        c.setRestored(1);
        c.setRestoreNote(note.trim());
        c.setRestoreTime(LocalDateTime.now());
        fire(c, MocRules.Action.RESTORE, "已恢复原状：" + note.trim());
    }

    // ============================== 安全管理（评估、退回、投用前检查） ==============================

    @Transactional(rollbackFor = Exception.class)
    public void assess(Long id, MocDTOs.Assess dto) {
        MocChange c = load(id);
        LecEvaluator.Result r;
        try {
            r = LecEvaluator.evaluate(dto.getLecL(), dto.getLecE(), dto.getLecC());
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        c.setLecL(dto.getLecL());
        c.setLecE(dto.getLecE());
        c.setLecC(dto.getLecC());
        c.setLecD(r.d());
        c.setRiskLevel(r.level().name());
        c.setRiskAnalysis(dto.getRiskAnalysis().trim());
        c.setMeasures(dto.getMeasures().trim());
        c.setAssessorName(me().getNickname());
        c.setAssessTime(LocalDateTime.now());
        c.setApprovalsNeeded(MocRules.approvalsNeeded(r.level()));
        fire(c, MocRules.Action.ASSESS, "风险评估：D = " + fmt(r.d()) + "，" + r.level().getLabel() + "，需要 " + c.getApprovalsNeeded() + " 人审批");
        List<Long> to = messageService.listPermissionUsers(PERM_APPROVE).stream().filter(u -> !u.equals(c.getApplicantId())).toList();
        if (!to.isEmpty()) {
            messageService.send(to, MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, c.getId(), "变更待审批：" + c.getTitle(),
                    r.level().getLabel() + "（D = " + fmt(r.d()) + "），需要 " + c.getApprovalsNeeded() + " 人审批", LINK));
        }
    }

    /** 评估时发现申请内容不完整，退回申请人修改 */
    @Transactional(rollbackFor = Exception.class)
    public void returnToApplicant(Long id, String comment) {
        MocChange c = load(id);
        if (!StringUtils.hasText(comment)) {
            throw new BizException("请写明退回原因");
        }
        fire(c, MocRules.Action.RETURN, "退回：" + comment.trim());
        messageService.send(List.of(c.getApplicantId()), MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, c.getId(),
                "变更申请被退回：" + c.getTitle(), comment.trim(), LINK));
    }

    @Transactional(rollbackFor = Exception.class)
    public void pssr(Long id, MocDTOs.Pssr dto) {
        MocChange c = load(id);
        if (!MocRules.PSSR.equals(c.getStatus())) {
            throw new BizException("当前不是投用前检查阶段");
        }
        String error = MocRules.checkPssr(dto.getResults(), dto.getNotes());
        if (error != null) {
            throw new BizException(error);
        }
        List<MocVOs.PssrItem> items = new ArrayList<>();
        for (int i = 0; i < MocRules.PSSR_ITEMS.size(); i++) {
            String note = dto.getNotes() == null || dto.getNotes().size() <= i ? null : dto.getNotes().get(i);
            items.add(new MocVOs.PssrItem(MocRules.PSSR_ITEMS.get(i), dto.getResults().get(i), StringUtils.hasText(note) ? note.trim() : null));
        }
        boolean pass = MocRules.pssrPassed(dto.getResults());
        c.setPssr(write(items));
        c.setPssrBy(me().getNickname());
        c.setPssrTime(LocalDateTime.now());
        String failed = items.stream().filter(x -> !x.ok()).map(x -> x.item() + "（" + x.note() + "）").collect(Collectors.joining("；"));
        if (pass) {
            c.setCloseTime(LocalDateTime.now());
            fire(c, MocRules.Action.PSSR_PASS, "投用前安全检查全部合格，正式投用" + (StringUtils.hasText(dto.getComment()) ? "。" + dto.getComment().trim() : ""));
            messageService.send(List.of(c.getApplicantId()), MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, c.getId(),
                    "变更已投用：" + c.getTitle(), Integer.valueOf(1).equals(c.getTemporary())
                            ? "这是临时变更，请在 " + c.getRestoreDate() + " 前恢复原状并登记" : "投用前安全检查全部合格", LINK));
        } else {
            fire(c, MocRules.Action.PSSR_FAIL, "投用前安全检查不合格：" + failed);
            messageService.send(List.of(c.getApplicantId()), MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, c.getId(),
                    "投用前安全检查不合格：" + c.getTitle(), "请整改后重新提交：" + failed, LINK));
        }
    }

    // ============================== 审批 ==============================

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String comment) {
        MocChange c = load(id);
        checkApprover(c);
        int approved = approvedThisRound(c, logs(id)).size() + 1;
        boolean done = approved >= c.getApprovalsNeeded();
        String text = "审批通过（" + approved + "/" + c.getApprovalsNeeded() + "）" + (StringUtils.hasText(comment) ? "：" + comment.trim() : "");
        if (done) {
            c.setStatus(MocRules.IMPLEMENTING);
        }
        save(c);
        log(c, MocRules.Action.APPROVE.name(), text);
        if (done) {
            messageService.send(List.of(c.getApplicantId()), MessageDraft.of(MessageCategory.NOTICE, BIZ_TYPE, c.getId(),
                    "变更已批准，可以实施：" + c.getTitle(), "请按批准的方案和控制措施实施，完成后提交投用前安全检查", LINK));
        }
    }

    /** 驳回：回到风险评估，审批重新计数 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String comment) {
        MocChange c = load(id);
        checkApprover(c);
        if (!StringUtils.hasText(comment)) {
            throw new BizException("请写明驳回原因");
        }
        c.setApprovalRound(c.getApprovalRound() + 1);
        fire(c, MocRules.Action.REJECT, "驳回：" + comment.trim());
        messageService.send(List.of(c.getApplicantId()), MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, c.getId(),
                "变更被驳回：" + c.getTitle(), comment.trim() + "。已退回风险评估", LINK));
        notifyPerm(PERM_EDIT, c, MessageCategory.TODO, "变更被驳回，请重新评估：" + c.getTitle(), comment.trim());
    }

    // ============================== 定时任务 / 大屏 ==============================

    /** 已投用、还没恢复的临时变更 */
    public List<MocChange> temporaryOpen() {
        return changeMapper.selectList(Wrappers.<MocChange>lambdaQuery()
                .eq(MocChange::getStatus, MocRules.CLOSED).eq(MocChange::getTemporary, 1).eq(MocChange::getRestored, 0)
                .select(MocChange::getId, MocChange::getChangeNo, MocChange::getTitle, MocChange::getRestoreDate, MocChange::getApplicantId));
    }

    // ============================== 内部方法 ==============================

    /** 当前用户能做的操作 */
    private List<String> actions(MocChange c, List<MocLog> logs) {
        List<String> list = new ArrayList<>();
        boolean applicant = isApplicant(c);
        boolean edit = StpUtil.hasPermission(PERM_EDIT);
        String st = c.getStatus();
        if (applicant && MocRules.DRAFT.equals(st)) {
            list.add(EDIT);
            list.add(MocRules.Action.SUBMIT.name());
        }
        if ((applicant || edit) && MocRules.Action.CANCEL.canFire(st)) {
            list.add(MocRules.Action.CANCEL.name());
        }
        if (edit && MocRules.ASSESSING.equals(st)) {
            list.add(MocRules.Action.ASSESS.name());
            list.add(MocRules.Action.RETURN.name());
        }
        if (MocRules.APPROVING.equals(st) && canApprove(c, logs)) {
            list.add(MocRules.Action.APPROVE.name());
            list.add(MocRules.Action.REJECT.name());
        }
        if (applicant && MocRules.IMPLEMENTING.equals(st)) {
            list.add(MocRules.Action.IMPLEMENTED.name());
        }
        if (edit && MocRules.PSSR.equals(st)) {
            list.add(MocRules.PSSR);
        }
        if ((applicant || edit) && MocRules.CLOSED.equals(st) && Integer.valueOf(1).equals(c.getTemporary()) && !Integer.valueOf(1).equals(c.getRestored())) {
            list.add(MocRules.Action.RESTORE.name());
        }
        return list;
    }

    /** 要当前用户处理的变更 ID（数据量小，在内存里算） */
    private Set<Long> todoIds() {
        Long me = StpUtil.getLoginIdAsLong();
        boolean edit = StpUtil.hasPermission(PERM_EDIT);
        boolean approve = StpUtil.hasPermission(PERM_APPROVE);
        LocalDate soon = LocalDate.now().plusDays(3);
        List<MocChange> open = changeMapper.selectList(Wrappers.<MocChange>lambdaQuery()
                .and(x -> x.notIn(MocChange::getStatus, MocRules.CLOSED, MocRules.CANCELLED)
                        .or(y -> y.eq(MocChange::getStatus, MocRules.CLOSED).eq(MocChange::getTemporary, 1).eq(MocChange::getRestored, 0)))
                .select(MocChange::getId, MocChange::getStatus, MocChange::getApplicantId, MocChange::getApprovalRound,
                        MocChange::getTemporary, MocChange::getRestored, MocChange::getRestoreDate));
        Set<Long> approvedByMe = open.isEmpty() || !approve ? Set.of()
                : logMapper.selectList(Wrappers.<MocLog>lambdaQuery().eq(MocLog::getOperatorId, me).eq(MocLog::getAction, MocRules.Action.APPROVE.name())
                        .in(MocLog::getChangeId, open.stream().map(MocChange::getId).toList()))
                .stream().filter(l -> open.stream().anyMatch(c -> c.getId().equals(l.getChangeId()) && Objects.equals(c.getApprovalRound(), l.getRound())))
                .map(MocLog::getChangeId).collect(Collectors.toSet());
        return open.stream().filter(c -> {
            boolean mine = me.equals(c.getApplicantId());
            return switch (c.getStatus()) {
                case MocRules.DRAFT, MocRules.IMPLEMENTING -> mine;
                case MocRules.ASSESSING, MocRules.PSSR -> edit;
                case MocRules.APPROVING -> approve && !mine && !approvedByMe.contains(c.getId());
                case MocRules.CLOSED -> mine && c.getRestoreDate() != null && !c.getRestoreDate().isAfter(soon);
                default -> false;
            };
        }).map(MocChange::getId).collect(Collectors.toSet());
    }

    private void checkApprover(MocChange c) {
        if (!MocRules.APPROVING.equals(c.getStatus())) {
            throw new BizException("当前不是审批阶段");
        }
        if (!StpUtil.hasPermission(PERM_APPROVE)) {
            throw new BizException(403, "没有变更审批权限");
        }
        if (isApplicant(c)) {
            throw new BizException("不能审批自己的变更申请");
        }
        if (approvedThisRound(c, logs(c.getId())).contains(StpUtil.getLoginIdAsLong())) {
            throw new BizException("你已经审批过了，需要另一位审批人");
        }
    }

    private boolean canApprove(MocChange c, List<MocLog> logs) {
        return StpUtil.hasPermission(PERM_APPROVE) && !isApplicant(c) && !approvedThisRound(c, logs).contains(StpUtil.getLoginIdAsLong());
    }

    /** 本轮已审批通过的人 */
    private static Set<Long> approvedThisRound(MocChange c, List<MocLog> logs) {
        return logs.stream().filter(l -> MocRules.Action.APPROVE.name().equals(l.getAction()) && Objects.equals(l.getRound(), c.getApprovalRound()))
                .map(MocLog::getOperatorId).collect(Collectors.toSet());
    }

    private Map<Long, Integer> approvedCounts(Collection<MocChange> list) {
        List<MocChange> approving = list.stream().filter(c -> MocRules.APPROVING.equals(c.getStatus())).toList();
        if (approving.isEmpty()) {
            return Map.of();
        }
        List<MocLog> logs = logMapper.selectList(Wrappers.<MocLog>lambdaQuery().eq(MocLog::getAction, MocRules.Action.APPROVE.name())
                .in(MocLog::getChangeId, approving.stream().map(MocChange::getId).toList()));
        return approving.stream().collect(Collectors.toMap(MocChange::getId, c -> approvedThisRound(c,
                logs.stream().filter(l -> l.getChangeId().equals(c.getId())).toList()).size()));
    }

    private static boolean restoreOverdue(MocChange c, LocalDate today) {
        return MocRules.CLOSED.equals(c.getStatus()) && Integer.valueOf(1).equals(c.getTemporary()) && !Integer.valueOf(1).equals(c.getRestored())
                && c.getRestoreDate() != null && c.getRestoreDate().isBefore(today);
    }

    private void fire(MocChange c, MocRules.Action a, String text) {
        if (!a.canFire(c.getStatus())) {
            throw new BizException("当前状态（" + MocRules.STATUSES.get(c.getStatus()) + "）不能" + a.label);
        }
        c.setStatus(a.to);
        save(c);
        log(c, a.name(), text);
    }

    private void check(MocDTOs.Save dto) {
        if (!MocRules.TYPES.containsKey(dto.getChangeType())) {
            throw new BizException("变更类型不正确");
        }
        String error = MocRules.checkTemporary(dto.isTemporary(), dto.getRestoreDate(), LocalDate.now());
        if (error != null) {
            throw new BizException(error);
        }
    }

    private static void fill(MocChange c, MocDTOs.Save dto) {
        c.setTitle(dto.getTitle().trim());
        c.setChangeType(dto.getChangeType());
        c.setTemporary(dto.isTemporary() ? 1 : 0);
        c.setRestoreDate(dto.isTemporary() ? dto.getRestoreDate() : null);
        c.setLocation(dto.getLocation().trim());
        c.setReason(dto.getReason().trim());
        c.setContent(dto.getContent().trim());
    }

    private void notifyPerm(String perm, MocChange c, String category, String title, String content) {
        List<Long> to = messageService.listPermissionUsers(perm).stream().filter(u -> !u.equals(StpUtil.getLoginIdAsLong())).toList();
        if (!to.isEmpty()) {
            messageService.send(to, MessageDraft.of(category, BIZ_TYPE, c.getId(), title, content, LINK));
        }
    }

    private void log(MocChange c, String action, String content) {
        SysUser me = me();
        MocLog l = new MocLog();
        l.setChangeId(c.getId());
        l.setAction(action);
        l.setRound(c.getApprovalRound());
        l.setContent(content.length() > 500 ? content.substring(0, 500) : content);
        l.setOperatorId(me.getId());
        l.setOperatorName(me.getNickname());
        logMapper.insert(l);
    }

    private List<MocLog> logs(Long id) {
        return logMapper.selectList(Wrappers.<MocLog>lambdaQuery().eq(MocLog::getChangeId, id));
    }

    private void save(MocChange c) {
        if (changeMapper.updateById(c) == 0) {
            throw new BizException("刚被别人处理过，请刷新后再试");
        }
    }

    private MocChange load(Long id) {
        MocChange c = changeMapper.selectById(id);
        if (c == null) {
            throw new BizException(404, "变更不存在");
        }
        return c;
    }

    private boolean isApplicant(MocChange c) {
        return Objects.equals(c.getApplicantId(), StpUtil.getLoginIdAsLong());
    }

    private void requireApplicant(MocChange c) {
        if (!isApplicant(c)) {
            throw new BizException(403, "只有申请人能操作");
        }
    }

    private SysUser me() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private static int count(List<MocChange> l, String status) {
        return (int) l.stream().filter(c -> status.equals(c.getStatus())).count();
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private List<MocVOs.PssrItem> read(String text) {
        try {
            return json.readValue(text, PSSR_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("投用前检查数据格式不正确", e);
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
