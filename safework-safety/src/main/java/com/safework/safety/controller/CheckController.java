package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.check.CheckRules;
import com.safework.safety.check.CheckService;
import com.safework.safety.check.CheckVOs;
import com.safework.safety.check.PlanSaveDTO;
import com.safework.safety.check.TaskQueryDTO;
import com.safework.safety.check.TaskSubmitDTO;
import com.safework.safety.check.TemplateSaveDTO;
import com.safework.safety.entity.CheckPlan;
import com.safework.safety.entity.CheckTask;
import com.safework.safety.entity.CheckTemplate;
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

@Tag(name = "安全检查", description = "检查表模板、检查计划、按周期生成检查任务、逐项检查、不合格项转隐患")
@RestController
@RequestMapping("/safety/check")
@RequiredArgsConstructor
public class CheckController {

    private final CheckService service;

    @SaCheckPermission(CheckService.PERM_LIST)
    @Operation(summary = "字典：检查类型、检查周期、检查项的隐患类别")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("checkTypes", CheckRules.CHECK_TYPES, "cycles", CheckRules.CYCLES,
                "categories", List.of("设备设施", "作业环境", "人员行为", "安全管理")));
    }

    // ---------------- 检查任务 ----------------

    @SaCheckPermission(CheckService.PERM_LIST)
    @Operation(summary = "检查任务分页：安全员看全部，其他人看分给自己的；state = PENDING（含逾期）/ OVERDUE / DONE；mine=true 只看自己的")
    @GetMapping("/task/page")
    public R<Page<CheckTask>> page(TaskQueryDTO query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(CheckService.PERM_LIST)
    @Operation(summary = "统计：待检查、逾期、本月完成、本月不合格项、本月转隐患、按时完成率")
    @GetMapping("/task/overview")
    public R<CheckVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @Operation(summary = "任务详情：待检查时返回检查项，已完成时返回逐项结果（检查人本人或安全员）")
    @GetMapping("/task/{id}")
    public R<CheckVOs.TaskDetail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    /** 检查人不一定有菜单权限（例如临时安排的人），所以和隐患整改一样只要求登录，是不是本人在 Service 里判断 */
    @OperLog(module = "安全检查", action = "提交检查")
    @Operation(summary = "提交检查结果：逐项合格 / 不合格，不合格项可以转隐患（只有检查人本人）")
    @PostMapping("/task/{id}/submit")
    public R<CheckVOs.SubmitResult> submit(@PathVariable Long id, @Valid @RequestBody TaskSubmitDTO dto) {
        return R.ok(service.submit(id, dto));
    }

    // ---------------- 检查表模板 ----------------

    @SaCheckPermission(CheckService.PERM_LIST)
    @Operation(summary = "检查表列表；enabled=true 只要启用的（新建计划时选择）")
    @GetMapping("/template/list")
    public R<List<CheckTemplate>> templates(@RequestParam(required = false) Boolean enabled) {
        return R.ok(service.templates(enabled));
    }

    @OperLog(module = "安全检查", action = "新建检查表")
    @SaCheckPermission(CheckService.PERM_EDIT)
    @Operation(summary = "新建检查表")
    @PostMapping("/template")
    public R<Long> createTemplate(@Valid @RequestBody TemplateSaveDTO dto) {
        return R.ok(service.saveTemplate(null, dto));
    }

    @OperLog(module = "安全检查", action = "修改检查表")
    @SaCheckPermission(CheckService.PERM_EDIT)
    @Operation(summary = "修改检查表（只影响以后生成的任务）")
    @PutMapping("/template/{id}")
    public R<Long> updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateSaveDTO dto) {
        return R.ok(service.saveTemplate(id, dto));
    }

    @OperLog(module = "安全检查", action = "启用 / 停用检查表")
    @SaCheckPermission(CheckService.PERM_EDIT)
    @Operation(summary = "启用 / 停用检查表（有启用的计划在用时不能停用）")
    @PutMapping("/template/{id}/status")
    public R<Void> templateStatus(@PathVariable Long id, @RequestParam int status) {
        service.templateStatus(id, status);
        return R.ok();
    }

    // ---------------- 检查计划 ----------------

    @SaCheckPermission(CheckService.PERM_EDIT)
    @Operation(summary = "检查计划列表")
    @GetMapping("/plan/list")
    public R<List<CheckPlan>> plans() {
        return R.ok(service.plans());
    }

    @OperLog(module = "安全检查", action = "新建检查计划")
    @SaCheckPermission(CheckService.PERM_EDIT)
    @Operation(summary = "新建检查计划；开始日期已到时立即生成第一张任务")
    @PostMapping("/plan")
    public R<Long> createPlan(@Valid @RequestBody PlanSaveDTO dto) {
        return R.ok(service.savePlan(null, dto));
    }

    @OperLog(module = "安全检查", action = "修改检查计划")
    @SaCheckPermission(CheckService.PERM_EDIT)
    @Operation(summary = "修改检查计划（已经生成的任务不受影响）")
    @PutMapping("/plan/{id}")
    public R<Long> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanSaveDTO dto) {
        return R.ok(service.savePlan(id, dto));
    }

    @OperLog(module = "安全检查", action = "启用 / 停用检查计划")
    @SaCheckPermission(CheckService.PERM_EDIT)
    @Operation(summary = "启用 / 停用检查计划（启用时如果到了时间立即生成任务）")
    @PutMapping("/plan/{id}/status")
    public R<Void> planStatus(@PathVariable Long id, @RequestParam int status) {
        service.planStatus(id, status);
        return R.ok();
    }
}
