package com.safework.safety.equipment;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.entity.Equipment;
import com.safework.safety.entity.EquipmentInspection;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.mapper.EquipmentInspectionMapper;
import com.safework.safety.mapper.EquipmentMapper;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.safety.service.HazardService;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * 设备设施：台账、定期检验、到期提醒、不合格停用并转隐患。
 * <pre>
 * 登记（可录入上次检验日期）──▶ 在用 ──检验合格──▶ 按周期排下次检验
 *                                │
 *                                └──检验不合格──▶ 自动停用 + 一键转隐患（重大 / 一般）──整改后复检合格──▶ 可以重新启用
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class EquipmentService {

    public static final String PERM_LIST = "equipment:list";
    public static final String PERM_EDIT = "equipment:edit";
    public static final String PERM_INSPECT = "equipment:inspect";
    static final String BIZ_TYPE = "EQUIPMENT";

    private final EquipmentMapper equipmentMapper;
    private final EquipmentInspectionMapper inspectionMapper;
    private final RiskPointMapper riskPointMapper;
    private final HazardService hazardService;
    private final HazardMapper hazardMapper;
    private final UserService userService;

    // ============================== 查询 ==============================

    public Page<Equipment> page(EquipmentQueryDTO q) {
        LocalDate today = LocalDate.now();
        String kw = q.getKeyword();
        LambdaQueryWrapper<Equipment> w = Wrappers.<Equipment>lambdaQuery()
                .eq(StringUtils.hasText(q.getCategory()), Equipment::getCategory, q.getCategory())
                .eq(StringUtils.hasText(q.getEquipType()), Equipment::getEquipType, q.getEquipType())
                .eq(q.getRiskPointId() != null, Equipment::getRiskPointId, q.getRiskPointId())
                .and(StringUtils.hasText(kw), x -> x.like(Equipment::getEquipCode, kw).or().like(Equipment::getName, kw)
                        .or().like(Equipment::getLocation, kw).or().like(Equipment::getRegisterNo, kw));
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(Equipment::getStatus, q.getStatus());
        } else {
            // 默认不看报废的
            w.ne(Equipment::getStatus, EquipmentRules.SCRAPPED);
        }
        stateFilter(w, q.getInspectState(), today);
        // 在用的排前面，越早到期越靠前，从没检验过的也靠前
        w.last("ORDER BY status = 'IN_USE' DESC, next_inspect_date IS NOT NULL, next_inspect_date, equip_code");
        Page<Equipment> page = equipmentMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
        page.getRecords().forEach(e -> mark(e, today));
        return page;
    }

    public EquipmentOverviewVO overview() {
        LocalDate today = LocalDate.now();
        return new EquipmentOverviewVO(
                equipmentMapper.selectCount(inUse()),
                equipmentMapper.selectCount(stateFilter(inUse(), EquipmentRules.OVERDUE, today)),
                equipmentMapper.selectCount(stateFilter(inUse(), EquipmentRules.DUE_SOON, today)),
                equipmentMapper.selectCount(stateFilter(inUse(), EquipmentRules.NEVER, today)),
                equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery().eq(Equipment::getStatus, EquipmentRules.STOPPED)));
    }

    public EquipmentDetailVO detail(Long id) {
        Equipment e = mark(load(id), LocalDate.now());
        List<EquipmentInspection> list = inspectionMapper.selectList(Wrappers.<EquipmentInspection>lambdaQuery()
                .eq(EquipmentInspection::getEquipmentId, id)
                .orderByDesc(EquipmentInspection::getInspectDate)
                .orderByDesc(EquipmentInspection::getCreateTime));
        return new EquipmentDetailVO(e, list);
    }

    // ============================== 台账 ==============================

    @Transactional(rollbackFor = Exception.class)
    public Long save(Long id, EquipmentSaveDTO dto) {
        EquipmentRules.TypeInfo type = EquipmentRules.TYPES.get(dto.getEquipType());
        if (type == null) {
            throw new BizException("设备类型不正确");
        }
        int cycle;
        try {
            cycle = EquipmentRules.cycle(dto.getEquipType(), dto.getCycleMonths());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ex.getMessage());
        }
        if (type.special() && !StringUtils.hasText(dto.getRegisterNo())) {
            throw new BizException(type.label() + "是特种设备，必须填写使用登记证编号");
        }
        String code = dto.getEquipCode().trim();
        if (equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getEquipCode, code)
                .ne(id != null, Equipment::getId, id)) > 0) {
            throw new BizException("设备位号【" + code + "】已经登记过了");
        }
        LocalDate today = LocalDate.now();
        if (dto.getInstallDate() != null && dto.getInstallDate().isAfter(today)) {
            throw new BizException("安装日期不能晚于今天");
        }
        Equipment e = id == null ? new Equipment() : load(id);
        if (EquipmentRules.SCRAPPED.equals(e.getStatus())) {
            throw new BizException("已报废的设备不能修改");
        }
        e.setEquipCode(code);
        e.setName(dto.getName().trim());
        e.setEquipType(dto.getEquipType());
        e.setCategory(type.category());
        e.setLocation(dto.getLocation().trim());
        if (dto.getResponsibleId() != null) {
            SysUser u = userService.getById(dto.getResponsibleId());
            if (!Integer.valueOf(1).equals(u.getStatus())) {
                throw new BizException("【" + u.getNickname() + "】的账号已停用，不能做设备责任人");
            }
            e.setResponsibleId(u.getId());
            e.setResponsibleName(u.getNickname());
        } else {
            e.setResponsibleId(null);
            e.setResponsibleName(null);
        }
        if (dto.getRiskPointId() != null) {
            RiskPoint p = riskPointMapper.selectById(dto.getRiskPointId());
            if (p == null) {
                throw new BizException("风险点不存在");
            }
            e.setRiskPointId(p.getId());
            e.setRiskPointName(p.getPointCode() + " " + p.getPointName());
        } else {
            e.setRiskPointId(null);
            e.setRiskPointName(null);
        }
        e.setRegisterNo(StringUtils.hasText(dto.getRegisterNo()) ? dto.getRegisterNo().trim() : null);
        e.setManufacturer(dto.getManufacturer());
        e.setModel(dto.getModel());
        e.setInstallDate(dto.getInstallDate());
        e.setRemark(dto.getRemark());
        boolean cycleChanged = e.getCycleMonths() != null && e.getCycleMonths() != cycle;
        e.setCycleMonths(cycle);
        if (id == null) {
            // 已在用的老设备：录入上次检验日期，系统接着排下次检验；新设备不填，状态是“从未检验”
            if (dto.getLastInspectDate() != null) {
                if (dto.getLastInspectDate().isAfter(today)) {
                    throw new BizException("上次检验日期不能晚于今天");
                }
                e.setLastInspectDate(dto.getLastInspectDate());
                e.setLastResult(EquipmentRules.PASS);
                e.setNextInspectDate(EquipmentRules.nextInspect(dto.getLastInspectDate(), cycle));
            }
            e.setStatus(EquipmentRules.IN_USE);
            equipmentMapper.insert(e);
        } else {
            // 改了检验周期，按上次合格的检验日期重新排
            if (cycleChanged && EquipmentRules.PASS.equals(e.getLastResult())) {
                e.setNextInspectDate(EquipmentRules.nextInspect(e.getLastInspectDate(), cycle));
            }
            equipmentMapper.updateById(e);
        }
        return e.getId();
    }

    /** 停用 / 启用 / 报废 */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, StatusDTO dto) {
        if (!EquipmentRules.STATUS_LABELS.containsKey(dto.getStatus())) {
            throw new BizException("状态不正确");
        }
        Equipment e = load(id);
        String error = EquipmentRules.checkStatusChange(e.getStatus(), dto.getStatus(), e.getLastResult(), e.getNextInspectDate(), LocalDate.now());
        if (error != null) {
            throw new BizException(error);
        }
        if (!EquipmentRules.IN_USE.equals(dto.getStatus()) && !StringUtils.hasText(dto.getReason())) {
            throw new BizException("请填写" + EquipmentRules.STATUS_LABELS.get(dto.getStatus()) + "原因");
        }
        e.setStatus(dto.getStatus());
        e.setStatusReason(StringUtils.hasText(dto.getReason()) ? dto.getReason().trim() : null);
        equipmentMapper.updateById(e);
    }

    // ============================== 检验 ==============================

    /**
     * 登记检验结果。
     * 合格：排下次检验；不合格：设备自动停用，可以同时上报隐患（和隐患页面上报完全一样，安全员会收到通知）
     */
    @Transactional(rollbackFor = Exception.class)
    public InspectionResultVO inspect(Long id, InspectionSaveDTO dto) {
        Equipment e = load(id);
        if (EquipmentRules.SCRAPPED.equals(e.getStatus())) {
            throw new BizException("已报废的设备不需要检验");
        }
        LocalDate today = LocalDate.now();
        LocalDate date = dto.getInspectDate();
        if (date.isAfter(today)) {
            throw new BizException("检验日期不能晚于今天");
        }
        if (e.getLastInspectDate() != null && date.isBefore(e.getLastInspectDate())) {
            throw new BizException("检验日期不能早于上次检验（" + e.getLastInspectDate() + "）");
        }
        boolean pass = EquipmentRules.PASS.equals(dto.getResult());
        if (!pass && !StringUtils.hasText(dto.getConclusion())) {
            throw new BizException("检验不合格，请写明不合格的原因");
        }
        EquipmentInspection r = new EquipmentInspection();
        r.setEquipmentId(id);
        r.setInspectDate(date);
        r.setResult(dto.getResult());
        r.setOrg(dto.getOrg());
        r.setReportNo(dto.getReportNo());
        r.setConclusion(StringUtils.hasText(dto.getConclusion()) ? dto.getConclusion().trim() : null);
        r.setRecorderName(userService.getById(StpUtil.getLoginIdAsLong()).getNickname());

        e.setLastInspectDate(date);
        e.setLastResult(dto.getResult());
        boolean stopped = false;
        if (pass) {
            LocalDate next = dto.getNextInspectDate();
            LocalDate latest = date.plusMonths(EquipmentRules.TYPES.get(e.getEquipType()).maxMonths());
            if (next == null) {
                next = EquipmentRules.nextInspect(date, e.getCycleMonths());
            } else if (!next.isAfter(date) || next.isAfter(latest)) {
                throw new BizException("下次检验日期要在检验日期之后，且不晚于 " + latest);
            }
            r.setNextInspectDate(next);
            e.setNextInspectDate(next);
        } else if (EquipmentRules.IN_USE.equals(e.getStatus())) {
            // 不合格的设备不能继续用
            e.setStatus(EquipmentRules.STOPPED);
            e.setStatusReason(date + " 检验不合格：" + r.getConclusion());
            stopped = true;
        }
        if (!pass && Boolean.TRUE.equals(dto.getToHazard())) {
            HazardReportDTO h = new HazardReportDTO();
            String title = "设备【" + e.getEquipCode() + " " + e.getName() + "】检验不合格";
            h.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
            String desc = EquipmentRules.TYPES.get(e.getEquipType()).label() + " " + e.getEquipCode() + " " + e.getName()
                    + " 于 " + date + " 检验不合格" + (StringUtils.hasText(r.getOrg()) ? "（" + r.getOrg() + "）" : "")
                    + "：" + r.getConclusion() + (stopped ? "。设备已停用，整改后需复检合格才能启用" : "");
            h.setDescription(desc.length() > 1000 ? desc.substring(0, 1000) : desc);
            h.setCategory("设备设施");
            h.setLevel(EquipmentRules.hazardLevel(e.getEquipType()));
            h.setLocation(e.getLocation());
            Long hazardId = hazardService.report(h);
            Hazard created = hazardMapper.selectById(hazardId);
            r.setHazardId(hazardId);
            r.setHazardNo(created.getHazardNo());
        }
        inspectionMapper.insert(r);
        equipmentMapper.updateById(e);
        return new InspectionResultVO(e.getNextInspectDate(), stopped, r.getHazardId(), r.getHazardNo());
    }

    // ============================== 内部方法 ==============================

    private static LambdaQueryWrapper<Equipment> inUse() {
        return Wrappers.<Equipment>lambdaQuery().eq(Equipment::getStatus, EquipmentRules.IN_USE);
    }

    static LambdaQueryWrapper<Equipment> stateFilter(LambdaQueryWrapper<Equipment> w, String state, LocalDate today) {
        if (!StringUtils.hasText(state)) {
            return w;
        }
        w.eq(Equipment::getStatus, EquipmentRules.IN_USE);
        switch (state) {
            case EquipmentRules.OVERDUE -> w.lt(Equipment::getNextInspectDate, today);
            case EquipmentRules.DUE_SOON -> w.between(Equipment::getNextInspectDate, today, today.plusDays(EquipmentRules.WARN_DAYS));
            case EquipmentRules.NEVER -> w.isNull(Equipment::getNextInspectDate);
            case EquipmentRules.OK -> w.gt(Equipment::getNextInspectDate, today.plusDays(EquipmentRules.WARN_DAYS));
            default -> throw new BizException("检验状态不正确");
        }
        return w;
    }

    static Equipment mark(Equipment e, LocalDate today) {
        e.setInspectState(EquipmentRules.state(e.getStatus(), e.getNextInspectDate(), today));
        e.setDaysLeft(e.getNextInspectDate() == null ? null : ChronoUnit.DAYS.between(today, e.getNextInspectDate()));
        return e;
    }

    private Equipment load(Long id) {
        Equipment e = equipmentMapper.selectById(id);
        if (e == null) {
            throw new BizException(404, "设备不存在");
        }
        return Objects.requireNonNull(e);
    }
}
