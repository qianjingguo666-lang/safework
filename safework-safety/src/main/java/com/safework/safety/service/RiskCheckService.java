package com.safework.safety.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.dto.RiskCheckDTO;
import com.safework.safety.dto.RiskCheckResultVO;
import com.safework.safety.dto.RiskTaskVO;
import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.RiskCheck;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.RiskCheckMapper;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.system.entity.SysUser;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 风险点巡检：按周期逐条确认管控措施是否有效。
 * 双重预防机制的闭环：风险点（事前识别）→ 巡检发现措施失效 → 一键转为隐患（进入隐患排查治理流程）。
 */
@Service
@RequiredArgsConstructor
public class RiskCheckService {

    public static final String PERM_CHECK = "risk:check";
    public static final String NORMAL = "NORMAL";
    public static final String ABNORMAL = "ABNORMAL";
    /** 风险点类型 → 隐患类别 */
    private static final Map<String, String> HAZARD_CATEGORY = Map.of("设备设施", "设备设施", "作业活动", "人员行为", "区域场所", "作业环境");

    private final RiskPointMapper pointMapper;
    private final RiskCheckMapper checkMapper;
    private final HazardMapper hazardMapper;
    private final HazardService hazardService;
    private final RiskService riskService;
    private final UserService userService;
    private final ObjectMapper json = new ObjectMapper();

    /** 巡检任务：责任人看自己负责的；安全员（能编辑风险点的人）看全部。按到期时间排序，逾期的在最前面 */
    public List<RiskTaskVO> tasks() {
        Long me = StpUtil.getLoginIdAsLong();
        boolean all = StpUtil.hasPermission(RiskService.PERM_EDIT);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        return pointMapper.selectList(Wrappers.<RiskPoint>lambdaQuery()
                        .eq(RiskPoint::getStatus, 1)
                        .eq(!all, RiskPoint::getResponsibleId, me)
                        .orderByAsc(RiskPoint::getNextCheckTime)
                        .last("LIMIT 300"))
                .stream()
                .map(p -> new RiskTaskVO(p.getId(), p.getPointCode(), p.getPointName(), p.getPointType(), p.getLocation(),
                        p.getRiskLevel(), p.getResponsibleName(), p.getCheckCycleDays(), p.getLastCheckTime(), p.getNextCheckTime(),
                        RiskRules.state(p.getNextCheckTime(), now), RiskRules.daysLeft(p.getNextCheckTime(), today),
                        RiskRules.measures(p.getControlMeasures())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public RiskCheckResultVO check(Long pointId, RiskCheckDTO dto) {
        RiskPoint p = riskService.load(pointId);
        if (!Objects.equals(p.getStatus(), 1)) {
            throw new BizException("风险点已停用，不需要巡检");
        }
        SysUser me = userService.getById(StpUtil.getLoginIdAsLong());
        if (!Objects.equals(p.getResponsibleId(), me.getId()) && !StpUtil.hasPermission(RiskService.PERM_EDIT)) {
            throw new BizException(403, "只有风险点责任人或安全员可以巡检");
        }
        // 逐条检查的结果必须和当前的管控措施一一对应，防止漏查
        List<String> measures = RiskRules.measures(p.getControlMeasures());
        if (dto.getItems().size() != measures.size()) {
            throw new BizException("管控措施有 " + measures.size() + " 条，请逐条确认（可能刚被修改过，请刷新）");
        }
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < measures.size(); i++) {
            RiskCheckDTO.Item it = dto.getItems().get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("measure", measures.get(i));
            m.put("ok", it.getOk());
            m.put("note", StringUtils.hasText(it.getNote()) ? it.getNote().trim() : null);
            items.add(m);
            if (!Boolean.TRUE.equals(it.getOk())) {
                failed.add(measures.get(i) + (StringUtils.hasText(it.getNote()) ? "（" + it.getNote().trim() + "）" : ""));
            }
        }
        boolean abnormal = !failed.isEmpty();
        boolean toHazard = abnormal && Boolean.TRUE.equals(dto.getToHazard());
        if (abnormal && !toHazard && !StringUtils.hasText(dto.getRemark())) {
            throw new BizException("有措施失效但不上报隐患时，请在备注里说明现场是怎么处理的");
        }

        RiskCheck c = new RiskCheck();
        c.setPointId(p.getId());
        c.setPointCode(p.getPointCode());
        c.setPointName(p.getPointName());
        c.setRiskLevel(p.getRiskLevel());
        c.setCheckerId(me.getId());
        c.setCheckerName(me.getNickname());
        c.setCheckTime(LocalDateTime.now());
        c.setResult(abnormal ? ABNORMAL : NORMAL);
        c.setItems(write(items));
        c.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark().trim() : null);

        if (toHazard) {
            // 转隐患：和在【隐患排查】页面上报完全一样（编号、记录、通知安全员）
            HazardReportDTO h = new HazardReportDTO();
            String title = StringUtils.hasText(dto.getHazardTitle()) ? dto.getHazardTitle().trim()
                    : "风险点【" + p.getPointName() + "】管控措施失效：" + failed.get(0);
            h.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
            String desc = "巡检风险点 " + p.getPointCode() + " " + p.getPointName() + "（" + p.getRiskLevel().getLabel()
                    + "）时发现以下管控措施失效：\n" + failed.stream().map(s -> "- " + s).collect(Collectors.joining("\n"))
                    + (c.getRemark() == null ? "" : "\n备注：" + c.getRemark());
            h.setDescription(desc.length() > 1000 ? desc.substring(0, 1000) : desc);
            h.setCategory(HAZARD_CATEGORY.getOrDefault(p.getPointType(), "安全管理"));
            // 重大、较大风险点的措施失效，按重大隐患处理
            h.setLevel(p.getRiskLevel() == RiskLevel.MAJOR || p.getRiskLevel() == RiskLevel.HIGH ? "MAJOR" : "GENERAL");
            h.setLocation(p.getLocation());
            h.setSpaceId(p.getSpaceId());
            Long hazardId = hazardService.report(h);
            Hazard created = hazardMapper.selectById(hazardId);
            c.setHazardId(hazardId);
            c.setHazardNo(created.getHazardNo());
        }
        checkMapper.insert(c);

        p.setLastCheckTime(c.getCheckTime());
        p.setNextCheckTime(c.getCheckTime().plusDays(p.getCheckCycleDays()));
        pointMapper.updateById(p);
        return new RiskCheckResultVO(c.getId(), c.getResult(), c.getHazardId(), c.getHazardNo(), p.getNextCheckTime());
    }

    /** 巡检记录：安全员看全部，其他人看自己做的 */
    public Page<RiskCheck> page(long current, long size, Long pointId, String result) {
        boolean all = StpUtil.hasPermission(RiskService.PERM_EDIT);
        return checkMapper.selectPage(new Page<>(current, size), Wrappers.<RiskCheck>lambdaQuery()
                .eq(pointId != null, RiskCheck::getPointId, pointId)
                .eq(StringUtils.hasText(result), RiskCheck::getResult, result)
                .eq(!all, RiskCheck::getCheckerId, StpUtil.getLoginIdAsLong())
                .orderByDesc(RiskCheck::getCheckTime));
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
