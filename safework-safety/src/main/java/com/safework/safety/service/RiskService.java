package com.safework.safety.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.LecOptionsVO;
import com.safework.safety.dto.RiskDetailVO;
import com.safework.safety.dto.RiskOverviewVO;
import com.safework.safety.dto.RiskQueryDTO;
import com.safework.safety.dto.RiskSaveDTO;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.entity.RiskCheck;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.excel.RiskExcelRow;
import com.safework.safety.mapper.RiskCheckMapper;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.excel.ExcelHelper;
import com.safework.system.service.DeptService;
import com.safework.system.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 风险分级管控：风险点台账、LEC 评估、四色分布 */
@Service
@RequiredArgsConstructor
public class RiskService {

    public static final String PERM_LIST = "risk:point:list";
    public static final String PERM_EDIT = "risk:point:edit";
    public static final List<String> POINT_TYPES = List.of("设备设施", "作业活动", "区域场所");

    private final RiskPointMapper pointMapper;
    private final RiskCheckMapper checkMapper;
    private final DeptService deptService;
    private final UserService userService;
    private final SpaceService spaceService;

    public LecOptionsVO lecOptions() {
        List<Map<String, Object>> levels = new ArrayList<>();
        for (RiskLevel l : RiskLevel.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", l.name());
            m.put("label", l.getLabel());
            m.put("color", l.getColor());
            m.put("controlLevel", l.getControlLevel());
            m.put("defaultCycleDays", l.getDefaultCycleDays());
            levels.add(m);
        }
        return new LecOptionsVO(LecEvaluator.L, LecEvaluator.E, LecEvaluator.C, levels);
    }

    public Page<RiskPoint> page(RiskQueryDTO q) {
        String kw = q.getKeyword();
        Set<Long> deptIds = q.getDeptId() == null ? null : deptService.selfAndDescendants(q.getDeptId());
        boolean overdue = Boolean.TRUE.equals(q.getOverdue());
        Page<RiskPoint> page = pointMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<RiskPoint>lambdaQuery()
                .and(StringUtils.hasText(kw), w -> w
                        .like(RiskPoint::getPointCode, kw).or()
                        .like(RiskPoint::getPointName, kw).or()
                        .like(RiskPoint::getLocation, kw))
                .eq(q.getRiskLevel() != null, RiskPoint::getRiskLevel, q.getRiskLevel())
                .eq(StringUtils.hasText(q.getPointType()), RiskPoint::getPointType, q.getPointType())
                .in(deptIds != null, RiskPoint::getDeptId, deptIds)
                .eq(q.getStatus() != null, RiskPoint::getStatus, q.getStatus())
                .eq(overdue, RiskPoint::getStatus, 1)
                .lt(overdue, RiskPoint::getNextCheckTime, LocalDateTime.now())
                // 风险高的排前面，同等级按 D 值
                .last("ORDER BY FIELD(risk_level, 'MAJOR', 'HIGH', 'GENERAL', 'LOW'), lec_d DESC, point_code"));
        fill(page.getRecords());
        return page;
    }

    public RiskDetailVO detail(Long id) {
        RiskPoint p = load(id);
        fill(List.of(p));
        List<RiskCheck> checks = checkMapper.selectList(Wrappers.<RiskCheck>lambdaQuery()
                .eq(RiskCheck::getPointId, id)
                .orderByDesc(RiskCheck::getCheckTime)
                .last("LIMIT 10"));
        return new RiskDetailVO(p, RiskRules.measures(p.getControlMeasures()), checks);
    }

    public Long create(RiskSaveDTO dto) {
        RiskPoint p = new RiskPoint();
        fill(p, dto);
        p.setStatus(1);
        // 新建的风险点按周期排第一次巡检
        p.setNextCheckTime(LocalDateTime.now().plusDays(p.getCheckCycleDays()));
        pointMapper.insert(p);
        return p.getId();
    }

    public void update(Long id, RiskSaveDTO dto) {
        RiskPoint p = load(id);
        Integer oldCycle = p.getCheckCycleDays();
        fill(p, dto);
        if (!Objects.equals(oldCycle, p.getCheckCycleDays())) {
            // 周期变了（比如重新评估后等级升高），按新周期重新排下次巡检
            LocalDateTime base = p.getLastCheckTime() == null ? LocalDateTime.now() : p.getLastCheckTime();
            p.setNextCheckTime(base.plusDays(p.getCheckCycleDays()));
        }
        pointMapper.updateById(p);
    }

    public void changeStatus(Long id, Integer status) {
        RiskPoint p = load(id);
        p.setStatus(Objects.equals(status, 1) ? 1 : 0);
        pointMapper.updateById(p);
    }

    private void fill(RiskPoint p, RiskSaveDTO dto) {
        if (!POINT_TYPES.contains(dto.getPointType())) {
            throw new BizException("类型只能是：" + String.join("、", POINT_TYPES));
        }
        String code = dto.getPointCode().trim();
        if (pointMapper.selectCount(Wrappers.<RiskPoint>lambdaQuery()
                .eq(RiskPoint::getPointCode, code)
                .ne(p.getId() != null, RiskPoint::getId, p.getId())) > 0) {
            throw new BizException("风险点编号 " + code + " 已存在");
        }
        LecEvaluator.Result r;
        try {
            r = LecEvaluator.evaluate(dto.getLecL(), dto.getLecE(), dto.getLecC());
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
        SysUser responsible = userService.getById(dto.getResponsibleId());
        p.setPointCode(code);
        p.setPointName(dto.getPointName().trim());
        p.setPointType(dto.getPointType());
        p.setLocation(dto.getLocation().trim());
        p.setDeptId(dto.getDeptId() != null ? dto.getDeptId() : responsible.getDeptId());
        p.setResponsibleId(responsible.getId());
        p.setResponsibleName(responsible.getNickname());
        if (dto.getSpaceId() != null) {
            ConfinedSpace s = spaceService.getById(dto.getSpaceId());
            p.setSpaceId(s.getId());
            p.setSpaceName(s.getSpaceName());
        } else {
            p.setSpaceId(null);
            p.setSpaceName(null);
        }
        p.setHazardFactors(trim(dto.getHazardFactors()));
        p.setPossibleAccidents(trim(dto.getPossibleAccidents()));
        p.setLecL(dto.getLecL());
        p.setLecE(dto.getLecE());
        p.setLecC(dto.getLecC());
        p.setLecD(r.d());
        p.setRiskLevel(r.level());
        if (RiskRules.measures(dto.getControlMeasures()).isEmpty()) {
            throw new BizException("请至少填写一条管控措施");
        }
        p.setControlMeasures(dto.getControlMeasures().trim());
        int cycle = dto.getCheckCycleDays() != null ? dto.getCheckCycleDays() : r.level().getDefaultCycleDays();
        // 不允许把高风险的巡检周期设得比默认值还长：重大风险至少每天查一次
        if (cycle > r.level().getDefaultCycleDays()) {
            throw new BizException(r.level().getLabel() + "的巡检周期不能超过 " + r.level().getDefaultCycleDays() + " 天");
        }
        p.setCheckCycleDays(cycle);
    }

    /** 四色分布：按位置分组；统计逾期、近 30 天巡检和异常 */
    public RiskOverviewVO overview() {
        LocalDateTime now = LocalDateTime.now();
        List<RiskPoint> points = pointMapper.selectList(Wrappers.<RiskPoint>lambdaQuery()
                .eq(RiskPoint::getStatus, 1)
                .last("ORDER BY location, FIELD(risk_level, 'MAJOR', 'HIGH', 'GENERAL', 'LOW'), point_code LIMIT 500"));
        Map<RiskLevel, Long> levels = new EnumMap<>(RiskLevel.class);
        for (RiskLevel l : RiskLevel.values()) {
            levels.put(l, 0L);
        }
        Map<String, List<RiskOverviewVO.Point>> groups = new LinkedHashMap<>();
        long overdue = 0;
        long today = 0;
        for (RiskPoint p : points) {
            levels.merge(p.getRiskLevel(), 1L, Long::sum);
            String state = RiskRules.state(p.getNextCheckTime(), now);
            if (RiskRules.OVERDUE.equals(state)) {
                overdue++;
            } else if (RiskRules.TODAY.equals(state)) {
                today++;
            }
            groups.computeIfAbsent(p.getLocation(), k -> new ArrayList<>())
                    .add(new RiskOverviewVO.Point(p.getId(), p.getPointCode(), p.getPointName(), p.getRiskLevel(), state));
        }
        LocalDateTime since = LocalDate.now().minusDays(29).atStartOfDay();
        long checks = checkMapper.selectCount(Wrappers.<RiskCheck>lambdaQuery().ge(RiskCheck::getCheckTime, since));
        long abnormal = checkMapper.selectCount(Wrappers.<RiskCheck>lambdaQuery()
                .ge(RiskCheck::getCheckTime, since).eq(RiskCheck::getResult, RiskCheckService.ABNORMAL));
        List<RiskOverviewVO.Group> list = groups.entrySet().stream()
                .map(e -> new RiskOverviewVO.Group(e.getKey(), e.getValue())).toList();
        return new RiskOverviewVO(points.size(), levels, overdue, today, checks, abnormal, list);
    }

    /** 导出风险分级管控清单（筛选条件和列表一致） */
    public void export(RiskQueryDTO q, HttpServletResponse response) {
        q.setCurrent(1);
        q.setSize(5000);
        List<RiskExcelRow> rows = page(q).getRecords().stream().map(p -> {
            RiskExcelRow row = new RiskExcelRow();
            row.setPointCode(p.getPointCode());
            row.setPointName(p.getPointName());
            row.setPointType(p.getPointType());
            row.setLocation(p.getLocation());
            row.setDeptName(p.getDeptName());
            row.setResponsibleName(p.getResponsibleName());
            row.setHazardFactors(p.getHazardFactors());
            row.setPossibleAccidents(p.getPossibleAccidents());
            row.setLecL(p.getLecL());
            row.setLecE(p.getLecE());
            row.setLecC(p.getLecC());
            row.setLecD(p.getLecD());
            row.setRiskLevel(p.getRiskLevel().getLabel() + "（" + p.getRiskLevel().getColor() + "）");
            row.setControlLevel(p.getRiskLevel().getControlLevel());
            row.setControlMeasures(p.getControlMeasures());
            row.setCheckCycleDays(p.getCheckCycleDays());
            row.setLastCheckTime(p.getLastCheckTime() == null ? "" : p.getLastCheckTime().format(ExcelHelper.CELL_TIME));
            row.setNextCheckTime(p.getNextCheckTime() == null ? "" : p.getNextCheckTime().format(ExcelHelper.CELL_TIME));
            return row;
        }).toList();
        ExcelHelper.write(response, "风险分级管控清单", RiskExcelRow.class, rows);
    }

    RiskPoint load(Long id) {
        RiskPoint p = pointMapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "风险点不存在");
        }
        return p;
    }

    private void fill(List<RiskPoint> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, String> deptNames = deptService.nameMap();
        LocalDateTime now = LocalDateTime.now();
        list.forEach(p -> {
            p.setDeptName(deptNames.get(p.getDeptId()));
            p.setCheckState(RiskRules.state(p.getNextCheckTime(), now));
        });
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
