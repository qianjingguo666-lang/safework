package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.drill.DrillDTOs;
import com.safework.safety.drill.DrillRules;
import com.safework.safety.drill.DrillService;
import com.safework.safety.drill.DrillVOs;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "应急演练", description = "安排演练和参演人员、手机签到、登记结果、按 AQ/T 9009 评估打分、预案适宜性、问题转隐患整改、年度演练计划")
@RestController
@RequestMapping("/safety/emergency/drill")
@RequiredArgsConstructor
public class DrillController {

    private final DrillService service;

    @Operation(summary = "字典：评估项目、参演角色、问题类别")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.of("scoreItems", DrillRules.SCORE_ITEMS, "roles", DrillRules.ROLES, "problemCategories", DrillRules.PROBLEM_CATEGORIES));
    }

    @SaCheckPermission(DrillService.PERM_LIST)
    @Operation(summary = "演练列表（计划中、待评估的排在前面）")
    @GetMapping("/list")
    public R<List<DrillVOs.Row>> list(DrillDTOs.Query q) {
        return R.ok(service.list(q));
    }

    @SaCheckPermission(DrillService.PERM_LIST)
    @Operation(summary = "年度演练计划完成情况（综合、专项预案每年 1 次，现场处置方案每年 2 次）")
    @GetMapping("/annual")
    public R<DrillVOs.Annual> annual(@RequestParam(required = false) Integer year) {
        return R.ok(service.annual(year == null ? LocalDate.now().getYear() : year));
    }

    @SaCheckPermission(DrillService.PERM_LIST)
    @Operation(summary = "概况：今年完成次数、没达到频次的预案、即将进行、待评估、未登记结果、未转整改的问题")
    @GetMapping("/overview")
    public R<DrillVOs.Overview> overview() {
        return R.ok(service.overview(LocalDate.now()));
    }

    @Operation(summary = "我的演练（参演人员手机签到用）")
    @GetMapping("/mine")
    public R<List<DrillVOs.Mine>> mine() {
        return R.ok(service.mine());
    }

    @Operation(summary = "详情（有预案查看权限或在参演名单里）")
    @GetMapping("/{id}")
    public R<DrillVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "应急演练", action = "安排演练")
    @SaCheckPermission(DrillService.PERM_EDIT)
    @Operation(summary = "安排演练（通知应急小组和参演人员）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody DrillDTOs.Save dto) {
        return R.ok(service.save(null, dto));
    }

    @OperLog(module = "应急演练", action = "修改演练安排")
    @SaCheckPermission(DrillService.PERM_EDIT)
    @Operation(summary = "修改演练安排（计划中）")
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody DrillDTOs.Save dto) {
        return R.ok(service.save(id, dto));
    }

    @OperLog(module = "应急演练", action = "演练签到")
    @Operation(summary = "参演人员签到（演练当天，开始前 2 小时起）")
    @PostMapping("/{id}/sign")
    public R<LocalDateTime> sign(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(service.sign(id));
    }

    @OperLog(module = "应急演练", action = "登记演练结果")
    @SaCheckPermission(DrillService.PERM_EDIT)
    @Operation(summary = "登记演练结果（确认实到人员，更新预案的最近演练时间）")
    @PostMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id, @Valid @RequestBody DrillDTOs.Complete dto) {
        service.complete(id, dto);
        return R.ok();
    }

    @OperLog(module = "应急演练", action = "演练评估")
    @SaCheckPermission(DrillService.PERM_EDIT)
    @Operation(summary = "评估打分（8 个项目，可以不适用）、预案适宜性、发现的问题")
    @PostMapping("/{id}/evaluate")
    public R<DrillRules.Result> evaluate(@PathVariable Long id, @Valid @RequestBody DrillDTOs.Evaluate dto) {
        return R.ok(service.evaluate(id, dto));
    }

    @OperLog(module = "应急演练", action = "演练问题转整改")
    @SaCheckPermission(DrillService.PERM_EDIT)
    @Operation(summary = "演练发现的问题转隐患整改")
    @PostMapping("/problem/{problemId}/hazard")
    public R<Long> toHazard(@PathVariable Long problemId, @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(service.toHazard(problemId));
    }

    @OperLog(module = "应急演练", action = "取消演练")
    @SaCheckPermission(DrillService.PERM_EDIT)
    @Operation(summary = "取消演练")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        service.cancel(id);
        return R.ok();
    }
}
