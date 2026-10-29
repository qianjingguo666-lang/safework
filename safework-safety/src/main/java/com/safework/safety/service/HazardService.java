package com.safework.safety.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.HazardActionDTO;
import com.safework.safety.dto.HazardDetailVO;
import com.safework.safety.dto.HazardQueryDTO;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.HazardLog;
import com.safework.safety.enums.HazardAction;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.HazardLogMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 隐患排查治理：上报 → 指派整改 → 提交整改 → 验收，形成闭环。
 * 和作业票一样：状态只能通过 {@link HazardAction} 流转、乐观锁防并发、每一步留痕。
 */
@Service
@RequiredArgsConstructor
public class HazardService {

    /** 安全员：指派、验收、撤销 */
    public static final String PERM_HANDLE = "safety:hazard:handle";

    private final HazardMapper hazardMapper;
    private final HazardLogMapper logMapper;
    private final SerialNoGenerator serialNo;
    private final SpaceService spaceService;
    private final UserService userService;
    private final HazardNotifier notifier;

    /** 数据权限：处理人（安全员、管理员）看全部；其他人看自己上报的和指派给自己整改的 */
    public Page<Hazard> page(HazardQueryDTO q) {
        Long me = StpUtil.getLoginIdAsLong();
        boolean viewAll = StpUtil.hasPermission(PERM_HANDLE);
        String kw = q.getKeyword();
        boolean overdue = Boolean.TRUE.equals(q.getOverdue());
        Page<Hazard> page = hazardMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<Hazard>lambdaQuery()
                .and(StringUtils.hasText(kw), w -> w
                        .like(Hazard::getHazardNo, kw).or()
                        .like(Hazard::getTitle, kw).or()
                        .like(Hazard::getLocation, kw))
                .eq(q.getStatus() != null, Hazard::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getLevel()), Hazard::getLevel, q.getLevel())
                .eq(overdue, Hazard::getStatus, HazardStatus.RECTIFYING)
                .lt(overdue, Hazard::getDeadline, LocalDateTime.now())
                .and(!viewAll, w -> w
                        .eq(Hazard::getReporterId, me).or()
                        .eq(Hazard::getRectifierId, me))
                .orderByDesc(Hazard::getCreateTime));
        page.getRecords().forEach(this::markOverdue);
        return page;
    }

    public HazardDetailVO detail(Long id) {
        Long me = StpUtil.getLoginIdAsLong();
        Hazard h = load(id);
        if (!StpUtil.hasPermission(PERM_HANDLE) && !me.equals(h.getReporterId()) && !me.equals(h.getRectifierId())) {
            throw new BizException(404, "隐患不存在");
        }
        markOverdue(h);
        List<HazardLog> logs = logMapper.selectList(Wrappers.<HazardLog>lambdaQuery()
                .eq(HazardLog::getHazardId, id)
                .orderByAsc(HazardLog::getCreateTime)
                .orderByAsc(HazardLog::getId));
        return new HazardDetailVO(h, logs, availableActions(h, me));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long report(HazardReportDTO dto) {
        SysUser me = currentUser();
        Hazard h = new Hazard();
        h.setHazardNo(serialNo.next("YH"));
        h.setTitle(dto.getTitle());
        h.setDescription(dto.getDescription());
        h.setCategory(dto.getCategory());
        h.setLevel(dto.getLevel());
        h.setLocation(dto.getLocation());
        if (dto.getSpaceId() != null) {
            ConfinedSpace s = spaceService.getById(dto.getSpaceId());
            h.setSpaceId(s.getId());
            h.setSpaceName(s.getSpaceName());
        }
        h.setReporterId(me.getId());
        h.setReporterName(me.getNickname());
        h.setStatus(HazardAction.REPORT.getTo());
        hazardMapper.insert(h);
        writeLog(h, HazardAction.REPORT, null, me, null);
        notifier.afterFire(h, HazardAction.REPORT, me, null);
        return h.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, HazardActionDTO dto) {
        if (dto.getRectifierId() == null || dto.getDeadline() == null) {
            throw new BizException("请选择整改责任人和整改期限");
        }
        if (!dto.getDeadline().isAfter(LocalDateTime.now())) {
            throw new BizException("整改期限必须晚于当前时间");
        }
        SysUser me = currentUser();
        Hazard h = load(id);
        SysUser rectifier = userService.getById(dto.getRectifierId());
        if (rectifier.getStatus() == null || rectifier.getStatus() != 1) {
            throw new BizException("整改责任人账号已停用");
        }
        h.setRectifierId(rectifier.getId());
        h.setRectifierName(rectifier.getNickname());
        h.setDeadline(dto.getDeadline());
        fire(h, HazardAction.ASSIGN, me, dto.getComment());
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, HazardActionDTO dto) {
        requireComment(dto, "撤销必须说明原因");
        SysUser me = currentUser();
        fire(load(id), HazardAction.REVOKE, me, dto.getComment());
    }

    @Transactional(rollbackFor = Exception.class)
    public void rectify(Long id, HazardActionDTO dto) {
        if (!StringUtils.hasText(dto.getRectifyMeasure())) {
            throw new BizException("请填写整改措施");
        }
        SysUser me = currentUser();
        Hazard h = load(id);
        if (!me.getId().equals(h.getRectifierId())) {
            throw new BizException(403, "只有整改责任人可以提交整改");
        }
        h.setRectifyMeasure(dto.getRectifyMeasure());
        h.setRectifyTime(LocalDateTime.now());
        fire(h, HazardAction.RECTIFY, me, dto.getRectifyMeasure());
    }

    @Transactional(rollbackFor = Exception.class)
    public void verifyPass(Long id, HazardActionDTO dto) {
        SysUser me = currentUser();
        Hazard h = load(id);
        requireNotRectifier(h, me);
        h.setVerifierId(me.getId());
        h.setVerifierName(me.getNickname());
        h.setVerifyTime(LocalDateTime.now());
        fire(h, HazardAction.VERIFY_PASS, me, dto.getComment());
    }

    @Transactional(rollbackFor = Exception.class)
    public void verifyFail(Long id, HazardActionDTO dto) {
        requireComment(dto, "验收不通过必须说明原因");
        SysUser me = currentUser();
        Hazard h = load(id);
        requireNotRectifier(h, me);
        fire(h, HazardAction.VERIFY_FAIL, me, dto.getComment());
    }

    // ========================= 内部方法 =========================

    private void fire(Hazard h, HazardAction action, SysUser me, String comment) {
        if (!action.canFire(h.getStatus())) {
            throw new BizException("当前状态【" + h.getStatus().getLabel() + "】不能执行【" + action.getLabel() + "】");
        }
        HazardStatus from = h.getStatus();
        h.setStatus(action.getTo());
        if (hazardMapper.updateById(h) == 0) {
            throw new BizException(409, "隐患已被他人处理，请刷新后重试");
        }
        writeLog(h, action, from, me, comment);
        notifier.afterFire(h, action, me, comment);
    }

    private List<HazardAction> availableActions(Hazard h, Long me) {
        boolean canHandle = StpUtil.hasPermission(PERM_HANDLE);
        boolean isRectifier = me.equals(h.getRectifierId());
        List<HazardAction> result = new ArrayList<>();
        for (HazardAction a : HazardAction.values()) {
            if (!a.canFire(h.getStatus())) {
                continue;
            }
            boolean allowed = switch (a) {
                case ASSIGN, REVOKE -> canHandle;
                case RECTIFY -> isRectifier;
                case VERIFY_PASS, VERIFY_FAIL -> canHandle && !isRectifier;
                case REPORT -> false;
            };
            if (allowed) {
                result.add(a);
            }
        }
        return result;
    }

    private void markOverdue(Hazard h) {
        h.setOverdue(h.getStatus() == HazardStatus.RECTIFYING
                && h.getDeadline() != null && h.getDeadline().isBefore(LocalDateTime.now()));
    }

    private Hazard load(Long id) {
        Hazard h = hazardMapper.selectById(id);
        if (h == null) {
            throw new BizException(404, "隐患不存在");
        }
        return h;
    }

    private SysUser currentUser() {
        return userService.getById(StpUtil.getLoginIdAsLong());
    }

    /** 职责分离：自己整改的隐患不能自己验收 */
    private void requireNotRectifier(Hazard h, SysUser me) {
        if (me.getId().equals(h.getRectifierId())) {
            throw new BizException(403, "不能验收自己整改的隐患");
        }
    }

    private static void requireComment(HazardActionDTO dto, String msg) {
        if (!StringUtils.hasText(dto.getComment())) {
            throw new BizException(msg);
        }
    }

    private void writeLog(Hazard h, HazardAction action, HazardStatus from, SysUser me, String comment) {
        HazardLog log = new HazardLog();
        log.setHazardId(h.getId());
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(h.getStatus());
        log.setOperatorId(me.getId());
        log.setOperatorName(me.getNickname());
        log.setComment(comment);
        logMapper.insert(log);
    }
}
