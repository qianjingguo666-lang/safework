package com.safework.safety.observation;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.SafetyObservation;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.SafetyObservationMapper;
import com.safework.safety.service.HazardService;
import com.safework.safety.service.SerialNoGenerator;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SafetyObservationService {
    public static final String PERM_LIST = "safety:observation:list";
    public static final String PERM_MANAGE = "safety:observation:manage";

    private final SafetyObservationMapper mapper;
    private final HazardMapper hazardMapper;
    private final HazardService hazardService;
    private final SerialNoGenerator serialNo;
    private final UserService userService;

    public Page<SafetyObservation> page(SafetyObservationDTOs.Query q) {
        long current = Math.max(1, q.getCurrent());
        long size = Math.min(100, Math.max(1, q.getSize()));
        Long me = StpUtil.getLoginIdAsLong();
        boolean canManage = StpUtil.hasPermission(PERM_MANAGE);
        return mapper.selectPage(new Page<>(current, size), Wrappers.<SafetyObservation>lambdaQuery()
                .and(StringUtils.hasText(q.getKeyword()), w -> w
                        .like(SafetyObservation::getObservationNo, q.getKeyword()).or()
                        .like(SafetyObservation::getTitle, q.getKeyword()).or()
                        .like(SafetyObservation::getLocation, q.getKeyword()))
                .eq(StringUtils.hasText(q.getStatus()), SafetyObservation::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getObservationType()), SafetyObservation::getObservationType, q.getObservationType())
                .eq(!canManage, SafetyObservation::getReporterId, me)
                .orderByDesc(SafetyObservation::getOccurTime)
                .orderByDesc(SafetyObservation::getId));
    }

    public SafetyObservationDetailVO detail(Long id) {
        SafetyObservation item = load(id);
        boolean canManage = StpUtil.hasPermission(PERM_MANAGE);
        if (!canManage && !item.getReporterId().equals(StpUtil.getLoginIdAsLong())) {
            throw new BizException(404, "观察记录不存在");
        }
        boolean canClose = canManage
                && SafetyObservationRules.ANALYZING.equals(item.getStatus())
                && StringUtils.hasText(item.getRootCause())
                && StringUtils.hasText(item.getCorrectiveAction());
        return new SafetyObservationDetailVO(item, canManage, canClose);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long report(SafetyObservationDTOs.Report dto) {
        String error = SafetyObservationRules.reportError(dto.getObservationType(), dto.getPotentialLevel(), dto.getOccurTime(), LocalDateTime.now());
        if (error != null) throw new BizException(error);

        SysUser reporter = userService.getById(StpUtil.getLoginIdAsLong());
        if (reporter == null) throw new BizException(401, "当前用户不存在");
        SafetyObservation item = new SafetyObservation();
        item.setObservationNo(serialNo.next("GC"));
        item.setObservationType(dto.getObservationType());
        item.setTitle(dto.getTitle().trim());
        item.setOccurTime(dto.getOccurTime());
        item.setLocation(dto.getLocation().trim());
        item.setDescription(dto.getDescription().trim());
        item.setPotentialLevel(dto.getPotentialLevel());
        item.setImmediateAction(trim(dto.getImmediateAction()));
        item.setStatus(SafetyObservationRules.REPORTED);
        item.setReporterId(reporter.getId());
        item.setReporterName(reporter.getNickname());
        mapper.insert(item);
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void analyze(Long id, SafetyObservationDTOs.Analysis dto) {
        requireManager();
        SafetyObservation item = loadOpen(id);
        boolean toHazard = Boolean.TRUE.equals(dto.getToHazard());
        String error = SafetyObservationRules.analysisError(item.getObservationType(), dto.getRootCause(), dto.getCorrectiveAction(), toHazard);
        if (error != null) throw new BizException(error);
        if (toHazard && item.getHazardId() != null) throw new BizException("该观察记录已经转为隐患");

        item.setRootCause(dto.getRootCause().trim());
        item.setCorrectiveAction(dto.getCorrectiveAction().trim());
        item.setStatus(SafetyObservationRules.ANALYZING);
        item.setAnalystId(StpUtil.getLoginIdAsLong());
        SysUser analyst = userService.getById(item.getAnalystId());
        item.setAnalystName(analyst == null ? "安全员" : analyst.getNickname());
        item.setAnalyzedTime(LocalDateTime.now());

        if (toHazard) {
            HazardReportDTO hazard = new HazardReportDTO();
            hazard.setTitle(item.getTitle());
            hazard.setDescription(item.getDescription() + (StringUtils.hasText(item.getImmediateAction())
                    ? "\n现场立即措施：" + item.getImmediateAction() : ""));
            hazard.setCategory(SafetyObservationRules.hazardCategory(item.getObservationType()));
            hazard.setLevel("HIGH".equals(item.getPotentialLevel()) ? "MAJOR" : "GENERAL");
            hazard.setLocation(item.getLocation());
            item.setHazardId(hazardService.report(hazard));
        }
        save(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        requireManager();
        SafetyObservation item = loadOpen(id);
        String error = SafetyObservationRules.closeError(item.getRootCause(), item.getCorrectiveAction());
        if (error != null) throw new BizException(error);
        if (item.getHazardId() != null) {
            Hazard hazard = hazardMapper.selectById(item.getHazardId());
            if (hazard == null || hazard.getStatus() != HazardStatus.CLOSED) {
                throw new BizException("关联隐患尚未验收闭环，暂时不能关闭观察记录");
            }
        }
        item.setStatus(SafetyObservationRules.CLOSED);
        item.setClosedTime(LocalDateTime.now());
        save(item);
    }

    private void requireManager() {
        if (!StpUtil.hasPermission(PERM_MANAGE)) {
            throw new BizException(403, "只有安全员和企业管理员可以分析或关闭观察记录");
        }
    }

    private SafetyObservation load(Long id) {
        SafetyObservation item = mapper.selectById(id);
        if (item == null) throw new BizException(404, "观察记录不存在");
        return item;
    }

    private SafetyObservation loadOpen(Long id) {
        SafetyObservation item = load(id);
        if (SafetyObservationRules.CLOSED.equals(item.getStatus())) {
            throw new BizException("观察记录已经关闭，不能再修改");
        }
        return item;
    }

    private void save(SafetyObservation item) {
        if (mapper.updateById(item) == 0) {
            throw new BizException("这条观察记录刚被别人更新过，请刷新后重试");
        }
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
