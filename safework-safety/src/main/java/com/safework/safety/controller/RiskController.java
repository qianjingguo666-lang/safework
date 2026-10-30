package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.dto.LecOptionsVO;
import com.safework.safety.dto.RiskCheckDTO;
import com.safework.safety.dto.RiskCheckResultVO;
import com.safework.safety.dto.RiskDetailVO;
import com.safework.safety.dto.RiskOverviewVO;
import com.safework.safety.dto.RiskQueryDTO;
import com.safework.safety.dto.RiskSaveDTO;
import com.safework.safety.dto.RiskTaskVO;
import com.safework.safety.entity.RiskCheck;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.service.RiskCheckService;
import com.safework.safety.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "风险分级管控", description = "风险点台账、LEC 评估定级、四色分布、周期巡检、措施失效转隐患")
@RestController
@RequestMapping("/safety/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;
    private final RiskCheckService checkService;

    @Operation(summary = "LEC 标准分值表和风险等级规则")
    @GetMapping("/lec-options")
    public R<LecOptionsVO> lecOptions() {
        return R.ok(riskService.lecOptions());
    }

    @SaCheckPermission(RiskService.PERM_LIST)
    @Operation(summary = "风险点分页（风险高的在前）")
    @GetMapping("/page")
    public R<Page<RiskPoint>> page(RiskQueryDTO query) {
        return R.ok(riskService.page(query));
    }

    @SaCheckPermission(RiskService.PERM_LIST)
    @Operation(summary = "四色分布和统计")
    @GetMapping("/overview")
    public R<RiskOverviewVO> overview() {
        return R.ok(riskService.overview());
    }

    @SaCheckPermission(RiskService.PERM_LIST)
    @Operation(summary = "导出风险分级管控清单")
    @GetMapping("/export")
    public void export(RiskQueryDTO query, HttpServletResponse response) {
        riskService.export(query, response);
    }

    @SaCheckPermission(RiskService.PERM_LIST)
    @Operation(summary = "风险点详情 + 最近巡检记录")
    @GetMapping("/{id}")
    public R<RiskDetailVO> detail(@PathVariable Long id) {
        return R.ok(riskService.detail(id));
    }

    @OperLog(module = "风险分级管控", action = "新增风险点")
    @SaCheckPermission(RiskService.PERM_EDIT)
    @Operation(summary = "新增风险点（按 LEC 自动定级）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RiskSaveDTO dto) {
        return R.ok(riskService.create(dto));
    }

    @OperLog(module = "风险分级管控", action = "修改风险点")
    @SaCheckPermission(RiskService.PERM_EDIT)
    @Operation(summary = "修改风险点 / 重新评估")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RiskSaveDTO dto) {
        riskService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "风险分级管控", action = "启用停用")
    @SaCheckPermission(RiskService.PERM_EDIT)
    @Operation(summary = "启用（1）/ 停用（0）")
    @PutMapping("/{id}/status")
    public R<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        riskService.changeStatus(id, status);
        return R.ok();
    }

    @SaCheckPermission(RiskCheckService.PERM_CHECK)
    @Operation(summary = "我的巡检任务（安全员看全部）")
    @GetMapping("/check/tasks")
    public R<List<RiskTaskVO>> tasks() {
        return R.ok(checkService.tasks());
    }

    @OperLog(module = "风险分级管控", action = "巡检")
    @SaCheckPermission(RiskCheckService.PERM_CHECK)
    @Operation(summary = "提交巡检：逐条确认管控措施，失效的可以同时上报隐患")
    @PostMapping("/{id}/check")
    public R<RiskCheckResultVO> check(@PathVariable Long id, @Valid @RequestBody RiskCheckDTO dto) {
        return R.ok(checkService.check(id, dto));
    }

    @SaCheckPermission(RiskCheckService.PERM_CHECK)
    @Operation(summary = "巡检记录（安全员看全部，其他人看自己的）")
    @GetMapping("/check/page")
    public R<Page<RiskCheck>> checks(@RequestParam(defaultValue = "1") long current, @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) Long pointId, @RequestParam(required = false) String result) {
        return R.ok(checkService.page(current, size, pointId, result));
    }
}
