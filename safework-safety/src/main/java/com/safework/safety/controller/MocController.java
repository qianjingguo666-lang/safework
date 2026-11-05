package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.moc.MocDTOs;
import com.safework.safety.moc.MocRules;
import com.safework.safety.moc.MocService;
import com.safework.safety.moc.MocVOs;
import com.safework.safety.service.LecEvaluator;
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

import java.util.Map;

@Tag(name = "变更管理", description = "MOC：变更申请 → 风险评估（LEC）→ 审批 → 实施 → 投用前安全检查（PSSR）→ 投用；临时变更到期恢复")
@RestController
@RequestMapping("/safety/moc")
@RequiredArgsConstructor
public class MocController {

    private final MocService service;

    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "字典：变更类型、状态、投用前检查项、LEC 分值")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("types", MocRules.TYPES, "statuses", MocRules.STATUSES, "pssrItems", MocRules.PSSR_ITEMS,
                "lec", Map.of("l", LecEvaluator.L, "e", LecEvaluator.E, "c", LecEvaluator.C)));
    }

    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "统计")
    @GetMapping("/overview")
    public R<MocVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "变更列表；todo=true 只看要我处理的")
    @GetMapping("/page")
    public R<MocVOs.Page> page(MocDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "变更详情：内容、风险评估、投用前检查、操作记录、我能做的操作")
    @GetMapping("/{id}")
    public R<MocVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    // ---------------- 申请人 ----------------

    @OperLog(module = "变更管理", action = "新建变更")
    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "新建变更申请（草稿）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody MocDTOs.Save dto) {
        return R.ok(service.create(dto));
    }

    @OperLog(module = "变更管理", action = "修改变更")
    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "修改草稿")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MocDTOs.Save dto) {
        service.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "变更管理", action = "提交变更")
    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "提交风险评估")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        service.submit(id);
        return R.ok();
    }

    @OperLog(module = "变更管理", action = "取消变更")
    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "取消（开始实施前；申请人或安全管理员）")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @Valid @RequestBody MocDTOs.Comment dto) {
        service.cancel(id, dto.getComment());
        return R.ok();
    }

    @OperLog(module = "变更管理", action = "实施完成")
    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "实施完成，提交投用前安全检查")
    @PostMapping("/{id}/implemented")
    public R<Void> implemented(@PathVariable Long id, @Valid @RequestBody MocDTOs.Comment dto) {
        service.implemented(id, dto.getComment());
        return R.ok();
    }

    @OperLog(module = "变更管理", action = "临时变更恢复")
    @SaCheckPermission(MocService.PERM_LIST)
    @Operation(summary = "临时变更恢复原状")
    @PostMapping("/{id}/restore")
    public R<Void> restore(@PathVariable Long id, @Valid @RequestBody MocDTOs.Comment dto) {
        service.restore(id, dto.getComment());
        return R.ok();
    }

    // ---------------- 安全管理 ----------------

    @OperLog(module = "变更管理", action = "风险评估")
    @SaCheckPermission(MocService.PERM_EDIT)
    @Operation(summary = "风险评估（LEC），较大、重大风险需要两人审批")
    @PostMapping("/{id}/assess")
    public R<Void> assess(@PathVariable Long id, @Valid @RequestBody MocDTOs.Assess dto) {
        service.assess(id, dto);
        return R.ok();
    }

    @OperLog(module = "变更管理", action = "退回申请人")
    @SaCheckPermission(MocService.PERM_EDIT)
    @Operation(summary = "退回申请人修改")
    @PostMapping("/{id}/return")
    public R<Void> returnBack(@PathVariable Long id, @Valid @RequestBody MocDTOs.Comment dto) {
        service.returnToApplicant(id, dto.getComment());
        return R.ok();
    }

    @OperLog(module = "变更管理", action = "投用前安全检查")
    @SaCheckPermission(MocService.PERM_EDIT)
    @Operation(summary = "投用前安全检查（PSSR）：全部合格投用，有不合格退回实施")
    @PostMapping("/{id}/pssr")
    public R<Void> pssr(@PathVariable Long id, @Valid @RequestBody MocDTOs.Pssr dto) {
        service.pssr(id, dto);
        return R.ok();
    }

    // ---------------- 审批 ----------------

    @OperLog(module = "变更管理", action = "审批通过")
    @SaCheckPermission(MocService.PERM_APPROVE)
    @Operation(summary = "审批通过（不能审批自己的；同一人一轮只能审批一次）")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @Valid @RequestBody MocDTOs.Comment dto) {
        service.approve(id, dto.getComment());
        return R.ok();
    }

    @OperLog(module = "变更管理", action = "驳回")
    @SaCheckPermission(MocService.PERM_APPROVE)
    @Operation(summary = "驳回，退回风险评估（审批重新计数）")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody MocDTOs.Comment dto) {
        service.reject(id, dto.getComment());
        return R.ok();
    }
}
