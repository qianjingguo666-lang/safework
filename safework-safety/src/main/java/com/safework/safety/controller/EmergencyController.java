package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.emergency.DrillCompleteDTO;
import com.safework.safety.emergency.DrillSaveDTO;
import com.safework.safety.emergency.EmergencyOverviewVO;
import com.safework.safety.emergency.EmergencyService;
import com.safework.safety.emergency.EventActionDTO;
import com.safework.safety.emergency.EventDetailVO;
import com.safework.safety.emergency.EventQueryDTO;
import com.safework.safety.emergency.EventReportDTO;
import com.safework.safety.emergency.PlanSaveDTO;
import com.safework.safety.entity.EmergencyDrill;
import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.EmergencyPlan;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "应急管理", description = "事故快报、应急响应、应急预案、应急演练")
@RestController
@RequestMapping("/safety/emergency")
@RequiredArgsConstructor
public class EmergencyController {

    private static final String PERM_LIST = "emergency:event:list";
    private static final String PERM_REPORT = "emergency:event:report";
    private static final String PERM_PLAN_LIST = "emergency:plan:list";

    private final EmergencyService service;

    // ============================== 事件 ==============================

    @SaCheckPermission(value = {PERM_LIST, PERM_PLAN_LIST}, mode = SaMode.OR)
    @Operation(summary = "统计：响应中的事件、今年事件数、预案数、演练逾期的预案数")
    @GetMapping("/overview")
    public R<EmergencyOverviewVO> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "事件分页（响应中的排在最前）")
    @GetMapping("/page")
    public R<Page<EmergencyEvent>> page(EventQueryDTO query) {
        return R.ok(service.events(query));
    }

    @SaCheckPermission(PERM_LIST)
    @Operation(summary = "事件详情 + 处置记录 + 已启动的预案")
    @GetMapping("/{id}")
    public R<EventDetailVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "应急管理", action = "事故快报")
    @SaCheckPermission(PERM_REPORT)
    @Operation(summary = "事故快报（任何员工都可以报）")
    @PostMapping
    public R<Long> report(@Valid @RequestBody EventReportDTO dto) {
        return R.ok(service.report(dto));
    }

    @OperLog(module = "应急管理", action = "启动应急响应")
    @SaCheckPermission(EmergencyService.PERM_HANDLE)
    @Operation(summary = "启动应急响应（选择预案，通知应急小组）")
    @PostMapping("/{id}/start")
    public R<Void> start(@PathVariable Long id, @Valid @RequestBody EventActionDTO dto) {
        service.start(id, dto);
        return R.ok();
    }

    @OperLog(module = "应急管理", action = "处置记录")
    @SaCheckPermission(EmergencyService.PERM_HANDLE)
    @Operation(summary = "追加处置记录")
    @PostMapping("/{id}/log")
    public R<Void> addLog(@PathVariable Long id, @Valid @RequestBody EventActionDTO dto) {
        service.addLog(id, dto);
        return R.ok();
    }

    @OperLog(module = "应急管理", action = "结束")
    @SaCheckPermission(EmergencyService.PERM_HANDLE)
    @Operation(summary = "结束（必须填写事后总结）")
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id, @Valid @RequestBody EventActionDTO dto) {
        service.close(id, dto);
        return R.ok();
    }

    // ============================== 预案 ==============================

    @SaCheckPermission(value = {PERM_PLAN_LIST, EmergencyService.PERM_HANDLE}, mode = SaMode.OR)
    @Operation(summary = "预案列表（含演练状态）；enabled=true 只要启用的（启动响应时选择）")
    @GetMapping("/plan/list")
    public R<List<EmergencyPlan>> plans(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Boolean enabled) {
        return R.ok(Boolean.TRUE.equals(enabled) ? service.enabledPlans() : service.plans(keyword));
    }

    @SaCheckPermission(value = {PERM_PLAN_LIST, EmergencyService.PERM_HANDLE}, mode = SaMode.OR)
    @Operation(summary = "预案详情")
    @GetMapping("/plan/{id}")
    public R<EmergencyPlan> plan(@PathVariable Long id) {
        return R.ok(service.plan(id));
    }

    @OperLog(module = "应急管理", action = "新增预案")
    @SaCheckPermission(EmergencyService.PERM_PLAN_EDIT)
    @Operation(summary = "新增预案")
    @PostMapping("/plan")
    public R<Long> createPlan(@Valid @RequestBody PlanSaveDTO dto) {
        return R.ok(service.savePlan(null, dto));
    }

    @OperLog(module = "应急管理", action = "修改预案")
    @SaCheckPermission(EmergencyService.PERM_PLAN_EDIT)
    @Operation(summary = "修改预案")
    @PutMapping("/plan/{id}")
    public R<Long> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanSaveDTO dto) {
        return R.ok(service.savePlan(id, dto));
    }

    @OperLog(module = "应急管理", action = "启用 / 停用预案")
    @SaCheckPermission(EmergencyService.PERM_PLAN_EDIT)
    @Operation(summary = "启用 / 停用预案")
    @PutMapping("/plan/{id}/status")
    public R<Void> planStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.changePlanStatus(id, body.get("status"));
        return R.ok();
    }

    // ============================== 演练 ==============================

    @SaCheckPermission(PERM_PLAN_LIST)
    @Operation(summary = "演练列表（计划中的排在最前）")
    @GetMapping("/drill/list")
    public R<List<EmergencyDrill>> drills(@RequestParam(required = false) Long planId) {
        return R.ok(service.drills(planId));
    }

    @OperLog(module = "应急管理", action = "安排演练")
    @SaCheckPermission(EmergencyService.PERM_PLAN_EDIT)
    @Operation(summary = "安排演练（通知应急小组）")
    @PostMapping("/drill")
    public R<Long> createDrill(@Valid @RequestBody DrillSaveDTO dto) {
        return R.ok(service.createDrill(dto));
    }

    @OperLog(module = "应急管理", action = "登记演练完成")
    @SaCheckPermission(EmergencyService.PERM_PLAN_EDIT)
    @Operation(summary = "登记演练完成（更新预案的最近演练时间）")
    @PostMapping("/drill/{id}/complete")
    public R<Void> completeDrill(@PathVariable Long id, @Valid @RequestBody DrillCompleteDTO dto) {
        service.completeDrill(id, dto);
        return R.ok();
    }

    @OperLog(module = "应急管理", action = "取消演练")
    @SaCheckPermission(EmergencyService.PERM_PLAN_EDIT)
    @Operation(summary = "取消演练")
    @PostMapping("/drill/{id}/cancel")
    public R<Void> cancelDrill(@PathVariable Long id) {
        service.cancelDrill(id);
        return R.ok();
    }
}
