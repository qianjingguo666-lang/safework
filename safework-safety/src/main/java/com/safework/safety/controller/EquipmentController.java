package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.entity.Equipment;
import com.safework.safety.equipment.EquipmentDetailVO;
import com.safework.safety.equipment.EquipmentOverviewVO;
import com.safework.safety.equipment.EquipmentQueryDTO;
import com.safework.safety.equipment.EquipmentRules;
import com.safework.safety.equipment.EquipmentSaveDTO;
import com.safework.safety.equipment.EquipmentService;
import com.safework.safety.equipment.InspectionResultVO;
import com.safework.safety.equipment.InspectionSaveDTO;
import com.safework.safety.equipment.StatusDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "设备设施", description = "特种设备、安全附件、报警仪器、消防设施台账和定期检验")
@RestController
@RequestMapping("/safety/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService service;

    @SaCheckPermission(EquipmentService.PERM_LIST)
    @Operation(summary = "设备类型：大类、默认检验周期、最长周期、是否特种设备（前端下拉和校验用同一份数据）")
    @GetMapping("/types")
    public R<List<Map<String, Object>>> types() {
        return R.ok(EquipmentRules.TYPES.entrySet().stream().map(en -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("value", en.getKey());
            m.put("label", en.getValue().label());
            m.put("category", en.getValue().category());
            m.put("categoryLabel", EquipmentRules.CATEGORIES.get(en.getValue().category()));
            m.put("defaultMonths", en.getValue().defaultMonths());
            m.put("maxMonths", en.getValue().maxMonths());
            m.put("special", en.getValue().special());
            return m;
        }).toList());
    }

    @SaCheckPermission(EquipmentService.PERM_LIST)
    @Operation(summary = "设备分页；inspectState = OVERDUE / DUE_SOON / NEVER / OK；默认不含报废")
    @GetMapping("/page")
    public R<Page<Equipment>> page(EquipmentQueryDTO query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(EquipmentService.PERM_LIST)
    @Operation(summary = "统计：在用、检验逾期、30 天内到期、从未检验、停用")
    @GetMapping("/overview")
    public R<EquipmentOverviewVO> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(EquipmentService.PERM_LIST)
    @Operation(summary = "设备详情 + 检验记录")
    @GetMapping("/{id}")
    public R<EquipmentDetailVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "设备设施", action = "登记设备")
    @SaCheckPermission(EquipmentService.PERM_EDIT)
    @Operation(summary = "登记设备（老设备可以录入上次检验日期）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody EquipmentSaveDTO dto) {
        return R.ok(service.save(null, dto));
    }

    @OperLog(module = "设备设施", action = "修改设备")
    @SaCheckPermission(EquipmentService.PERM_EDIT)
    @Operation(summary = "修改设备；改了检验周期会按上次合格的检验日期重新排")
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody EquipmentSaveDTO dto) {
        return R.ok(service.save(id, dto));
    }

    @OperLog(module = "设备设施", action = "停用 / 启用 / 报废")
    @SaCheckPermission(EquipmentService.PERM_EDIT)
    @Operation(summary = "停用、启用、报废（启用要求最近一次检验合格且没有逾期）")
    @PutMapping("/{id}/status")
    public R<Void> status(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        service.changeStatus(id, dto);
        return R.ok();
    }

    @OperLog(module = "设备设施", action = "登记检验")
    @SaCheckPermission(EquipmentService.PERM_INSPECT)
    @Operation(summary = "登记检验结果：合格排下次检验；不合格自动停用，可以同时上报隐患")
    @PostMapping("/{id}/inspection")
    public R<InspectionResultVO> inspect(@PathVariable Long id, @Valid @RequestBody InspectionSaveDTO dto) {
        return R.ok(service.inspect(id, dto));
    }
}
