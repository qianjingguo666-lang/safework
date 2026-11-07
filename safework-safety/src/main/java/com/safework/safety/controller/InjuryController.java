package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.injury.InjuryDTOs;
import com.safework.safety.injury.InjuryRules;
import com.safework.safety.injury.InjuryService;
import com.safework.safety.injury.InjuryVOs;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "工伤管理", description = "工伤登记、30 日内申请认定倒计时、认定结果、劳动能力鉴定、停工留薪期、费用台账（《工伤保险条例》）")
@RestController
@RequestMapping("/safety/injury")
@RequiredArgsConstructor
public class InjuryController {

    private final InjuryService service;

    @SaCheckPermission(InjuryService.PERM_LIST)
    @Operation(summary = "字典：状态、费用类别、支付方")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("status", InjuryRules.STATUS, "costCategories", InjuryRules.COST_CATEGORIES, "payers", InjuryRules.PAYERS));
    }

    @SaCheckPermission(InjuryService.PERM_LIST)
    @Operation(summary = "概况：今年工伤人数、待申请认定、超期、处理中、今年费用")
    @GetMapping("/overview")
    public R<InjuryVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(InjuryService.PERM_LIST)
    @Operation(summary = "工伤列表（带申请认定倒计时和费用合计）")
    @GetMapping("/page")
    public R<InjuryVOs.Page> page(InjuryDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(InjuryService.PERM_LIST)
    @Operation(summary = "详情：倒计时、费用台账、伤残等级和一次性伤残补助金、可以做的操作")
    @GetMapping("/{id}")
    public R<InjuryVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "工伤管理", action = "登记工伤")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "登记工伤（可以关联事故快报）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody InjuryDTOs.Save dto) {
        return R.ok(service.save(null, dto));
    }

    @OperLog(module = "工伤管理", action = "修改工伤")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "修改基本信息（申请认定前）")
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody InjuryDTOs.Save dto) {
        return R.ok(service.save(id, dto));
    }

    @OperLog(module = "工伤管理", action = "申请工伤认定")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "记录已申请工伤认定")
    @PostMapping("/{id}/apply")
    public R<Void> apply(@PathVariable Long id, @Valid @RequestBody InjuryDTOs.Apply dto) {
        service.apply(id, dto);
        return R.ok();
    }

    @OperLog(module = "工伤管理", action = "登记认定结果")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "登记认定结果（认定为工伤 / 不予认定）")
    @PostMapping("/{id}/decide")
    public R<Void> decide(@PathVariable Long id, @Valid @RequestBody InjuryDTOs.Decide dto) {
        service.decide(id, dto);
        return R.ok();
    }

    @OperLog(module = "工伤管理", action = "登记劳动能力鉴定")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "登记劳动能力鉴定（伤残等级，0 = 未达到等级）")
    @PostMapping("/{id}/appraise")
    public R<Void> appraise(@PathVariable Long id, @Valid @RequestBody InjuryDTOs.Appraise dto) {
        service.appraise(id, dto);
        return R.ok();
    }

    @OperLog(module = "工伤管理", action = "设置停工留薪期")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "停工留薪期（一般不超过 12 个月，经确认延长最长 24 个月）和复工日期")
    @PutMapping("/{id}/leave")
    public R<Void> leave(@PathVariable Long id, @Valid @RequestBody InjuryDTOs.Leave dto) {
        service.leave(id, dto);
        return R.ok();
    }

    @OperLog(module = "工伤管理", action = "结案")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "结案")
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id, @Valid @RequestBody InjuryDTOs.Close dto) {
        service.close(id, dto.getNote());
        return R.ok();
    }

    @OperLog(module = "工伤管理", action = "登记费用")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "登记一笔费用")
    @PostMapping("/{id}/cost")
    public R<Long> addCost(@PathVariable Long id, @Valid @RequestBody InjuryDTOs.Cost dto) {
        return R.ok(service.addCost(id, dto));
    }

    @OperLog(module = "工伤管理", action = "删除费用")
    @SaCheckPermission(InjuryService.PERM_EDIT)
    @Operation(summary = "删除一笔费用（结案前）")
    @DeleteMapping("/cost/{costId}")
    public R<Void> removeCost(@PathVariable Long costId) {
        service.removeCost(costId);
        return R.ok();
    }
}
