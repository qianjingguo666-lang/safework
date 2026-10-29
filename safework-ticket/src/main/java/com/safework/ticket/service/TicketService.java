package com.safework.ticket.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.service.SpaceService;
import com.safework.system.entity.SysUser;
import com.safework.system.service.PermissionService;
import com.safework.system.service.UserService;
import com.safework.ticket.dto.TicketActionDTO;
import com.safework.ticket.dto.TicketDetailVO;
import com.safework.ticket.dto.TicketQueryDTO;
import com.safework.ticket.dto.TicketSaveDTO;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.entity.WorkTicketLog;
import com.safework.ticket.enums.TicketAction;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.mapper.WorkTicketLogMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 作业票业务。三条规则贯穿始终：
 * 1. 状态只能通过 {@link TicketAction} 流转（状态机）
 * 2. 谁能做什么：申请人编辑/提交/取消，安全员审批（不能审批自己的票），指定监护人开工/完工
 * 3. 并发更新靠 @Version 乐观锁，更新行数为 0 说明被别人抢先处理了
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    public static final String PERM_ADD = "ticket:add";
    public static final String PERM_APPROVE = "ticket:approve";
    private static final String ROLE_GUARDIAN = "guardian";

    private final WorkTicketMapper ticketMapper;
    private final WorkTicketLogMapper logMapper;
    private final TicketNoGenerator ticketNoGenerator;
    private final UserService userService;
    private final PermissionService permissionService;
    private final SpaceService spaceService;

    // ========================= 查询 =========================

    /**
     * 分页查询，带数据权限：
     * 有审批权限的人（安全员、管理员）看全部，其他人只看自己申请的或自己监护的
     */
    public Page<WorkTicket> page(TicketQueryDTO q) {
        Long me = StpUtil.getLoginIdAsLong();
        boolean viewAll = StpUtil.hasPermission(PERM_APPROVE);
        String kw = q.getKeyword();
        return ticketMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<WorkTicket>lambdaQuery()
                .eq(q.getStatus() != null, WorkTicket::getStatus, q.getStatus())
                .eq(q.getTicketType() != null, WorkTicket::getTicketType, q.getTicketType())
                .and(StringUtils.hasText(kw), w -> w
                        .like(WorkTicket::getTicketNo, kw).or()
                        .like(WorkTicket::getTitle, kw).or()
                        .like(WorkTicket::getLocation, kw))
                .and(!viewAll, w -> w
                        .eq(WorkTicket::getApplicantId, me).or()
                        .eq(WorkTicket::getGuardianId, me))
                .orderByDesc(WorkTicket::getCreateTime));
    }

    public TicketDetailVO detail(Long id) {
        Long me = StpUtil.getLoginIdAsLong();
        WorkTicket t = load(id);
        boolean viewAll = StpUtil.hasPermission(PERM_APPROVE);
        if (!viewAll && !me.equals(t.getApplicantId()) && !me.equals(t.getGuardianId())) {
            // 没有查看权限时也返回“不存在”，不暴露这张票是否存在
            throw new BizException(404, "作业票不存在");
        }
        List<WorkTicketLog> logs = logMapper.selectList(Wrappers.<WorkTicketLog>lambdaQuery()
                .eq(WorkTicketLog::getTicketId, id)
                .orderByAsc(WorkTicketLog::getCreateTime)
                .orderByAsc(WorkTicketLog::getId));
        return new TicketDetailVO(t, logs, availableActions(t, me));
    }

    /** 当前企业的监护人列表，新建作业票时下拉选择 */
    public List<SysUser> guardians() {
        return userService.listByRoleCode(ROLE_GUARDIAN);
    }

    // ========================= 申请人操作 =========================

    @Transactional(rollbackFor = Exception.class)
    public Long create(TicketSaveDTO dto) {
        SysUser me = currentUser();
        WorkTicket t = new WorkTicket();
        apply(t, dto, me.getId());
        t.setTicketNo(ticketNoGenerator.next(dto.getTicketType()));
        t.setApplicantId(me.getId());
        t.setApplicantName(me.getNickname());
        t.setStatus(TicketAction.CREATE.getTo());
        ticketMapper.insert(t);
        writeLog(t, TicketAction.CREATE, null, me, null);
        return t.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TicketSaveDTO dto) {
        SysUser me = currentUser();
        WorkTicket t = load(id);
        requireApplicant(t, me);
        if (t.getTicketType() != dto.getTicketType()) {
            throw new BizException("作业类型创建后不能修改，请取消后重新申请");
        }
        checkCanFire(t, TicketAction.EDIT);
        apply(t, dto, me.getId());
        fire(t, TicketAction.EDIT, me, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        SysUser me = currentUser();
        WorkTicket t = load(id);
        requireApplicant(t, me);
        checkCanFire(t, TicketAction.SUBMIT);
        if (!t.getPlanEndTime().isAfter(LocalDateTime.now())) {
            throw new BizException("计划结束时间已过，请修改计划时间");
        }
        if (t.getTicketType() == TicketType.CONFINED_SPACE && t.getSpaceId() == null) {
            throw new BizException("受限空间作业必须选择台账中的受限空间");
        }
        if (t.getTicketType().isGasRequired()) {
            GasChecker.checkValues(t);
        }
        fire(t, TicketAction.SUBMIT, me, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, TicketActionDTO dto) {
        SysUser me = currentUser();
        WorkTicket t = load(id);
        requireApplicant(t, me);
        fire(t, TicketAction.CANCEL, me, dto.getComment());
    }

    // ========================= 审批人操作 =========================

    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, TicketActionDTO dto) {
        SysUser me = currentUser();
        WorkTicket t = load(id);
        requireNotApplicant(t, me);
        checkSpaceConflict(t);
        t.setApproverId(me.getId());
        t.setApproverName(me.getNickname());
        fire(t, TicketAction.APPROVE, me, dto.getComment());
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, TicketActionDTO dto) {
        if (!StringUtils.hasText(dto.getComment())) {
            throw new BizException("驳回必须填写原因");
        }
        SysUser me = currentUser();
        WorkTicket t = load(id);
        requireNotApplicant(t, me);
        t.setApproverId(me.getId());
        t.setApproverName(me.getNickname());
        fire(t, TicketAction.REJECT, me, dto.getComment());
    }

    // ========================= 监护人操作 =========================

    @Transactional(rollbackFor = Exception.class)
    public void start(Long id, TicketActionDTO dto) {
        SysUser me = currentUser();
        WorkTicket t = load(id);
        requireGuardian(t, me);
        checkCanFire(t, TicketAction.START);
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(t.getPlanEndTime())) {
            throw new BizException("已超过计划结束时间，不能开工，请重新申请");
        }
        // 开工前现场复测：传了气体数据就用最新的
        if (dto.getGasOxygen() != null || dto.getGasLel() != null) {
            t.setGasOxygen(dto.getGasOxygen());
            t.setGasLel(dto.getGasLel());
            t.setGasH2s(dto.getGasH2s());
            t.setGasCheckTime(now);
        }
        if (t.getTicketType().isGasRequired()) {
            GasChecker.checkFresh(t);
        }
        t.setActualStartTime(now);
        fire(t, TicketAction.START, me, dto.getComment());
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, TicketActionDTO dto) {
        SysUser me = currentUser();
        WorkTicket t = load(id);
        requireGuardian(t, me);
        t.setActualEndTime(LocalDateTime.now());
        fire(t, TicketAction.COMPLETE, me, dto.getComment());
    }

    // ========================= 内部方法 =========================

    /**
     * 同一个受限空间，计划时间有重叠的有效作业票（已批准、作业中）只能有一张，
     * 避免两个班组同时进入同一个空间、各自以为对方已经做好隔离。
     * 时间重叠判断：A.开始 < B.结束 并且 A.结束 > B.开始
     */
    private void checkSpaceConflict(WorkTicket t) {
        if (t.getSpaceId() == null) {
            return;
        }
        List<WorkTicket> conflicts = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                .eq(WorkTicket::getSpaceId, t.getSpaceId())
                .ne(WorkTicket::getId, t.getId())
                .in(WorkTicket::getStatus, TicketStatus.APPROVED, TicketStatus.IN_PROGRESS)
                .lt(WorkTicket::getPlanStartTime, t.getPlanEndTime())
                .gt(WorkTicket::getPlanEndTime, t.getPlanStartTime()));
        if (!conflicts.isEmpty()) {
            WorkTicket c = conflicts.get(0);
            throw new BizException("【" + t.getSpaceName() + "】在计划时间内已有有效作业票 " + c.getTicketNo()
                    + "（" + c.getStatus().getLabel() + "），不能重复批准");
        }
    }

    /** 状态流转的唯一入口：校验 → 改状态 → 乐观锁更新 → 记日志 */
    private void fire(WorkTicket t, TicketAction action, SysUser me, String comment) {
        checkCanFire(t, action);
        TicketStatus from = t.getStatus();
        if (action.getTo() != null) {
            t.setStatus(action.getTo());
        }
        int rows = ticketMapper.updateById(t);
        if (rows == 0) {
            throw new BizException(409, "作业票已被他人处理，请刷新后重试");
        }
        writeLog(t, action, from, me, comment);
    }

    private void checkCanFire(WorkTicket t, TicketAction action) {
        if (!action.canFire(t.getStatus())) {
            throw new BizException("当前状态【" + t.getStatus().getLabel() + "】不能执行【" + action.getLabel() + "】");
        }
    }

    /** 当前用户能对这张票做哪些操作（和上面的校验规则保持一致） */
    private List<TicketAction> availableActions(WorkTicket t, Long me) {
        boolean isApplicant = me.equals(t.getApplicantId());
        boolean isGuardian = me.equals(t.getGuardianId());
        boolean canAdd = StpUtil.hasPermission(PERM_ADD);
        boolean canApprove = StpUtil.hasPermission(PERM_APPROVE);
        List<TicketAction> result = new ArrayList<>();
        for (TicketAction a : TicketAction.values()) {
            if (!a.canFire(t.getStatus())) {
                continue;
            }
            boolean allowed = switch (a) {
                case EDIT, SUBMIT, CANCEL -> isApplicant && canAdd;
                case APPROVE, REJECT -> canApprove && !isApplicant;
                case START, COMPLETE -> isGuardian;
                case CREATE -> false;
            };
            if (allowed) {
                result.add(a);
            }
        }
        return result;
    }

    private void apply(WorkTicket t, TicketSaveDTO dto, Long applicantId) {
        if (!dto.getPlanEndTime().isAfter(dto.getPlanStartTime())) {
            throw new BizException("计划结束时间必须晚于开始时间");
        }
        if (Objects.equals(dto.getGuardianId(), applicantId)) {
            throw new BizException("监护人不能由申请人兼任");
        }
        SysUser guardian = userService.getById(dto.getGuardianId());
        if (!permissionService.getRoleCodes(guardian.getId()).contains(ROLE_GUARDIAN)) {
            throw new BizException("所选人员【" + guardian.getNickname() + "】不是监护人");
        }
        t.setTicketType(dto.getTicketType());
        if (dto.getTicketType() == TicketType.CONFINED_SPACE && dto.getSpaceId() != null) {
            ConfinedSpace space = spaceService.getEnabled(dto.getSpaceId());
            t.setSpaceId(space.getId());
            t.setSpaceName(space.getSpaceName());
        }
        t.setTitle(dto.getTitle());
        t.setLocation(dto.getLocation());
        t.setWorkContent(dto.getWorkContent());
        t.setSafetyMeasures(dto.getSafetyMeasures());
        t.setPlanStartTime(dto.getPlanStartTime());
        t.setPlanEndTime(dto.getPlanEndTime());
        t.setGuardianId(guardian.getId());
        t.setGuardianName(guardian.getNickname());
        t.setGasOxygen(dto.getGasOxygen());
        t.setGasLel(dto.getGasLel());
        t.setGasH2s(dto.getGasH2s());
        t.setGasCheckTime(dto.getGasCheckTime());
    }

    private WorkTicket load(Long id) {
        // 租户插件会自动加 tenant_id 条件，别的企业的票查不到
        WorkTicket t = ticketMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "作业票不存在");
        }
        return t;
    }

    private SysUser currentUser() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    private void requireApplicant(WorkTicket t, SysUser me) {
        if (!me.getId().equals(t.getApplicantId())) {
            throw new BizException(403, "只有申请人可以执行此操作");
        }
    }

    private void requireNotApplicant(WorkTicket t, SysUser me) {
        if (me.getId().equals(t.getApplicantId())) {
            throw new BizException(403, "不能审批自己申请的作业票");
        }
    }

    private void requireGuardian(WorkTicket t, SysUser me) {
        if (!me.getId().equals(t.getGuardianId())) {
            throw new BizException(403, "只有指定的监护人可以执行此操作");
        }
    }

    private void writeLog(WorkTicket t, TicketAction action, TicketStatus from, SysUser me, String comment) {
        WorkTicketLog log = new WorkTicketLog();
        log.setTicketId(t.getId());
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(t.getStatus());
        log.setOperatorId(me.getId());
        log.setOperatorName(me.getNickname());
        log.setComment(comment);
        logMapper.insert(log);
    }
}
