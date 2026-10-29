package com.safework.safety.excel;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.dto.HazardQueryDTO;
import com.safework.safety.dto.SpaceQueryDTO;
import com.safework.safety.dto.SpaceSaveDTO;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.entity.Hazard;
import com.safework.safety.mapper.ConfinedSpaceMapper;
import com.safework.safety.service.HazardService;
import com.safework.safety.service.SpaceService;
import com.safework.system.excel.ExcelHelper;
import com.safework.system.excel.ImportResult;
import com.safework.system.service.DeptService;
import com.safework.system.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 受限空间台账导入导出、隐患台账导出 */
@Service
@RequiredArgsConstructor
public class SafetyExcelService {

    /** 导出上限：再多就应该加筛选条件了 */
    private static final int MAX_EXPORT = 5000;
    private static final int MAX_IMPORT = 1000;
    private static final List<String> SPACE_TYPES = List.of("储罐", "反应釜", "污水池", "地下有限空间", "管道", "其他");
    private static final Map<String, String> RISK_TO_CODE = Map.of("高", "HIGH", "高风险", "HIGH", "中", "MEDIUM",
            "中风险", "MEDIUM", "低", "LOW", "低风险", "LOW");
    private static final Map<String, String> RISK_TO_LABEL = Map.of("HIGH", "高", "MEDIUM", "中", "LOW", "低");
    /** 用来标记“名称重复，无法确定是哪一个” */
    private static final Long AMBIGUOUS = -1L;

    private final SpaceService spaceService;
    private final HazardService hazardService;
    private final ConfinedSpaceMapper spaceMapper;
    private final DeptService deptService;
    private final UserService userService;

    // ======================== 受限空间 ========================

    /** 导出：和列表用同样的筛选条件 */
    public void exportSpaces(SpaceQueryDTO q, HttpServletResponse response) {
        q.setCurrent(1);
        q.setSize(MAX_EXPORT);
        List<SpaceExcelRow> rows = spaceService.page(q).getRecords().stream().map(s -> {
            SpaceExcelRow r = new SpaceExcelRow();
            r.setSpaceCode(s.getSpaceCode());
            r.setSpaceName(s.getSpaceName());
            r.setSpaceType(s.getSpaceType());
            r.setLocation(s.getLocation());
            r.setDeptName(s.getDeptName());
            r.setResponsibleName(s.getResponsibleName());
            r.setMedium(s.getMedium());
            r.setRiskLevel(RISK_TO_LABEL.getOrDefault(s.getRiskLevel(), s.getRiskLevel()));
            r.setStatus(Integer.valueOf(1).equals(s.getStatus()) ? "启用" : "停用");
            r.setRemark(s.getRemark());
            return r;
        }).toList();
        ExcelHelper.write(response, "受限空间台账", SpaceExcelRow.class, rows);
    }

    public void spaceTemplate(HttpServletResponse response) {
        ExcelHelper.writeTemplate(response, "受限空间台账", SpaceExcelRow.class, List.of(
                "1. 在第一个工作表填写数据，第 1 行表头不要修改；一次最多 " + MAX_IMPORT + " 行",
                "2. 必填：空间编号、空间名称、空间类型、位置、风险等级",
                "3. 空间类型只能是：" + String.join("、", SPACE_TYPES),
                "4. 风险等级填：高 / 中 / 低",
                "5. 所属部门填部门名称、责任人填系统里的姓名（和【系统管理】里一致），可以不填",
                "6. 空间编号在企业内不能重复，也不能和已有台账重复",
                "7. 状态可以不填，默认启用",
                "8. 任何一行有错误都不会导入，系统会列出所有错误，改完后重新导入即可",
                "示例：CS-101 | 3# 甲醇储罐 T-103 | 储罐 | 罐区西侧 | 储运车间 | 李四（作业负责人） | 甲醇 | 高 | 启用 | 每季度清罐一次"));
    }

    /**
     * 导入：先校验全部行，只要有一行错就一行都不导入（返回所有错误）；全部正确才在一个事务里批量新增。
     * 这样用户不用去猜“前面成功了几行、从哪行开始重导”。
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importSpaces(MultipartFile file) {
        List<SpaceExcelRow> rows = ExcelHelper.read(file, SpaceExcelRow.class, MAX_IMPORT);
        ImportResult result = new ImportResult();
        result.setTotal(rows.size());
        if (rows.isEmpty()) {
            result.addError(2, "Excel 里没有数据");
            return result;
        }
        Set<String> existingCodes = spaceMapper.selectList(Wrappers.<ConfinedSpace>lambdaQuery()
                        .select(ConfinedSpace::getSpaceCode))
                .stream().map(ConfinedSpace::getSpaceCode).collect(Collectors.toSet());
        Map<String, Long> deptIds = uniqueMap(deptService.listAll().stream()
                .filter(d -> d.getDeptName() != null)
                .map(d -> Map.entry(d.getDeptName(), d.getId())).toList());
        Map<String, Long> userIds = uniqueMap(userService.options().stream()
                .filter(u -> u.getNickname() != null)
                .map(u -> Map.entry(u.getNickname(), u.getId())).toList());

        Set<String> seenCodes = new HashSet<>();
        List<SpaceSaveDTO> valid = new ArrayList<>();
        List<Boolean> disabled = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            SpaceExcelRow r = rows.get(i);
            int rowNo = i + 2;
            List<String> errs = new ArrayList<>();
            String code = trim(r.getSpaceCode());
            if (code == null) {
                errs.add("空间编号不能为空");
            } else if (code.length() > 32) {
                errs.add("空间编号不超过 32 位");
            } else if (existingCodes.contains(code)) {
                errs.add("空间编号 " + code + " 已存在");
            } else if (!seenCodes.add(code)) {
                errs.add("空间编号 " + code + " 在文件里重复");
            }
            if (trim(r.getSpaceName()) == null) {
                errs.add("空间名称不能为空");
            }
            if (!SPACE_TYPES.contains(trim(r.getSpaceType()))) {
                errs.add("空间类型只能是 " + String.join("/", SPACE_TYPES));
            }
            if (trim(r.getLocation()) == null) {
                errs.add("位置不能为空");
            }
            String risk = RISK_TO_CODE.get(trim(r.getRiskLevel()) == null ? "" : trim(r.getRiskLevel()));
            if (risk == null) {
                errs.add("风险等级只能填 高 / 中 / 低");
            }
            Long deptId = lookup(deptIds, trim(r.getDeptName()), "部门", errs);
            Long userId = lookup(userIds, trim(r.getResponsibleName()), "责任人", errs);
            String status = trim(r.getStatus());
            if (status != null && !"启用".equals(status) && !"停用".equals(status)) {
                errs.add("状态只能填 启用 / 停用");
            }
            if (!errs.isEmpty()) {
                result.addError(rowNo, String.join("；", errs));
                continue;
            }
            SpaceSaveDTO dto = new SpaceSaveDTO();
            dto.setSpaceCode(code);
            dto.setSpaceName(trim(r.getSpaceName()));
            dto.setSpaceType(trim(r.getSpaceType()));
            dto.setLocation(trim(r.getLocation()));
            dto.setDeptId(deptId);
            dto.setResponsibleId(userId);
            dto.setMedium(trim(r.getMedium()));
            dto.setRiskLevel(risk);
            dto.setRemark(trim(r.getRemark()));
            valid.add(dto);
            disabled.add("停用".equals(status));
        }
        if (result.hasErrors()) {
            return result;
        }
        for (int i = 0; i < valid.size(); i++) {
            Long id = spaceService.create(valid.get(i));
            if (disabled.get(i)) {
                spaceService.changeStatus(id, 0);
            }
        }
        result.setSuccess(valid.size());
        return result;
    }

    // ======================== 隐患 ========================

    /** 导出隐患台账：和列表一样带数据权限（安全员导出全部，其他人只导出和自己有关的） */
    public void exportHazards(HazardQueryDTO q, HttpServletResponse response) {
        q.setCurrent(1);
        q.setSize(MAX_EXPORT);
        List<HazardExcelRow> rows = hazardService.page(q).getRecords().stream().map(this::toRow).toList();
        ExcelHelper.write(response, "隐患台账", HazardExcelRow.class, rows);
    }

    private HazardExcelRow toRow(Hazard h) {
        HazardExcelRow r = new HazardExcelRow();
        r.setHazardNo(h.getHazardNo());
        r.setTitle(h.getTitle());
        r.setLevel("MAJOR".equals(h.getLevel()) ? "重大" : "一般");
        r.setCategory(h.getCategory());
        r.setLocation(h.getLocation());
        r.setSpaceName(h.getSpaceName());
        r.setStatus(h.getStatus() == null ? null : h.getStatus().getLabel());
        r.setOverdue(Boolean.TRUE.equals(h.getOverdue()) ? "是" : "");
        r.setReporterName(h.getReporterName());
        r.setReportTime(fmt(h.getCreateTime()));
        r.setRectifierName(h.getRectifierName());
        r.setDeadline(fmt(h.getDeadline()));
        r.setRectifyMeasure(h.getRectifyMeasure());
        r.setRectifyTime(fmt(h.getRectifyTime()));
        r.setVerifierName(h.getVerifierName());
        r.setVerifyTime(fmt(h.getVerifyTime()));
        return r;
    }

    // ======================== 工具 ========================

    /** 名称 → ID；同名的标记为 AMBIGUOUS（导入时提示“有重名，请在系统里手动选择”） */
    private static Map<String, Long> uniqueMap(List<Map.Entry<String, Long>> entries) {
        Map<String, Long> map = new HashMap<>();
        for (Map.Entry<String, Long> e : entries) {
            if (e.getKey() != null) {
                map.merge(e.getKey().trim(), e.getValue(), (a, b) -> AMBIGUOUS);
            }
        }
        return map;
    }

    private static Long lookup(Map<String, Long> map, String name, String what, List<String> errs) {
        if (name == null) {
            return null;
        }
        Long id = map.get(name);
        if (id == null) {
            errs.add(what + "【" + name + "】不存在");
        } else if (AMBIGUOUS.equals(id)) {
            errs.add(what + "【" + name + "】有重名，请导入后在系统里手动选择");
            return null;
        }
        return id;
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(ExcelHelper.CELL_TIME);
    }
}
