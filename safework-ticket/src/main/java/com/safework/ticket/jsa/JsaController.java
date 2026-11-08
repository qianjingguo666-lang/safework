package com.safework.ticket.jsa;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.ticket.enums.TicketType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "作业安全分析 JSA", description = "作业步骤 → 危害 → 风险（L×S）→ 控制措施；编写、AI 起草、审核发布、开作业票时引用、满 1 年复审")
@RestController
@RequestMapping("/ticket/jsa")
@RequiredArgsConstructor
public class JsaController {

    private final JsaService service;

    @SaCheckPermission(JsaService.PERM_LIST)
    @Operation(summary = "字典：状态、风险等级、作业类型")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        Map<String, String> types = new LinkedHashMap<>();
        Arrays.stream(TicketType.values()).forEach(t -> types.put(t.name(), t.getLabel()));
        return R.ok(Map.of("status", JsaRules.STATUS, "levels", JsaRules.LEVELS, "ticketTypes", types));
    }

    @SaCheckPermission(JsaService.PERM_LIST)
    @Operation(summary = "概况：已发布、审核中、该复审、草稿")
    @GetMapping("/overview")
    public R<JsaVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(JsaService.PERM_LIST)
    @Operation(summary = "JSA 列表（审核中、被驳回、草稿排在前面）")
    @GetMapping("/page")
    public R<JsaVOs.Page> page(JsaDTOs.Query q) {
        return R.ok(service.page(q));
    }

    @Operation(summary = "开作业票时可以引用的 JSA（已发布，作业类型一致或通用）；能开票的人都能查")
    @GetMapping("/usable")
    public R<List<JsaVOs.Option>> usable(@RequestParam(required = false) String ticketType) {
        return R.ok(service.usable(ticketType));
    }

    @Operation(summary = "详情（作业票详情里也要显示引用的 JSA，所以登录即可）")
    @GetMapping("/{id}")
    public R<JsaVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "JSA", action = "新建")
    @SaCheckPermission(JsaService.PERM_EDIT)
    @Operation(summary = "新建（草稿）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody JsaDTOs.Save dto) {
        return R.ok(service.save(null, dto));
    }

    @OperLog(module = "JSA", action = "修改")
    @SaCheckPermission(JsaService.PERM_EDIT)
    @Operation(summary = "修改（草稿、被驳回，只有编写人）")
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody JsaDTOs.Save dto) {
        return R.ok(service.save(id, dto));
    }

    @OperLog(module = "JSA", action = "提交审核")
    @SaCheckPermission(JsaService.PERM_EDIT)
    @Operation(summary = "提交审核")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        service.submit(id);
        return R.ok();
    }

    @OperLog(module = "JSA", action = "审核通过")
    @SaCheckPermission(JsaService.PERM_REVIEW)
    @Operation(summary = "审核通过并发布（不能审核自己写的；复审版本发布后旧版本自动停用）")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        service.approve(id);
        return R.ok();
    }

    @OperLog(module = "JSA", action = "驳回")
    @SaCheckPermission(JsaService.PERM_REVIEW)
    @Operation(summary = "驳回（要写原因）")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody JsaDTOs.Reject dto) {
        service.reject(id, dto.getComment());
        return R.ok();
    }

    @OperLog(module = "JSA", action = "停用")
    @SaCheckPermission(JsaService.PERM_REVIEW)
    @Operation(summary = "停用（以后开票不能再引用）")
    @PostMapping("/{id}/archive")
    public R<Void> archive(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        service.archive(id);
        return R.ok();
    }

    @OperLog(module = "JSA", action = "复制")
    @SaCheckPermission(JsaService.PERM_EDIT)
    @Operation(summary = "复制成新草稿（复审，或照着改一个相似作业）")
    @PostMapping("/{id}/copy")
    public R<Long> copy(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return R.ok(service.copy(id));
    }

    @OperLog(module = "JSA", action = "删除")
    @SaCheckPermission(JsaService.PERM_EDIT)
    @Operation(summary = "删除自己的草稿或被驳回的")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
