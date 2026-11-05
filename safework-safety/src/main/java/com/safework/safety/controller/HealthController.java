package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.entity.OhExam;
import com.safework.safety.entity.OhFactor;
import com.safework.safety.health.HealthDTOs;
import com.safework.safety.health.HealthRules;
import com.safework.safety.health.HealthService;
import com.safework.safety.health.HealthVOs;
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

@Tag(name = "职业健康", description = "危害因素、岗位接害、体检记录、监护台账、异常处理")
@RestController
@RequestMapping("/safety/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService service;

    @SaCheckPermission(HealthService.PERM_LIST)
    @Operation(summary = "字典：危害因素类别、体检类型、体检结论")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("categories", HealthRules.CATEGORIES, "examTypes", HealthRules.EXAM_TYPES,
                "conclusions", HealthRules.CONCLUSIONS));
    }

    // ---------------- 台账 ----------------

    @SaCheckPermission(HealthService.PERM_LIST)
    @Operation(summary = "监护台账（管理员看全部，其他人看自己的）；state = NEVER / OVERDUE / DUE_SOON / OK / NEED / ABNORMAL")
    @GetMapping("/ledger")
    public R<HealthVOs.LedgerPage> ledger(HealthDTOs.LedgerQuery query) {
        return R.ok(service.ledger(query));
    }

    @SaCheckPermission(HealthService.PERM_LIST)
    @Operation(summary = "统计：接害人数、从未体检、逾期、30 天内到期、异常未处理、禁忌证仍在岗")
    @GetMapping("/overview")
    public R<HealthVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @Operation(summary = "我的职业健康：接触的危害因素、下次体检、我的体检记录（登录即可）")
    @GetMapping("/mine")
    public R<HealthVOs.Mine> mine() {
        return R.ok(service.mine());
    }

    // ---------------- 体检记录 ----------------

    @SaCheckPermission(HealthService.PERM_LIST)
    @Operation(summary = "体检记录（管理员看全部，其他人看自己的）；open=true 只看待处理的异常")
    @GetMapping("/exam/page")
    public R<Page<OhExam>> examPage(HealthDTOs.ExamQuery query) {
        return R.ok(service.examPage(query));
    }

    @OperLog(module = "职业健康", action = "登记体检")
    @SaCheckPermission(HealthService.PERM_EDIT)
    @Operation(summary = "登记体检结果：异常结论进入待处理，通知本人和管理员")
    @PostMapping("/exam")
    public R<Long> saveExam(@Valid @RequestBody HealthDTOs.ExamSave dto) {
        return R.ok(service.saveExam(dto));
    }

    @OperLog(module = "职业健康", action = "处理体检异常")
    @SaCheckPermission(HealthService.PERM_EDIT)
    @Operation(summary = "登记异常的处理措施（复查结果、调岗、职业病诊断……）")
    @PostMapping("/exam/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody HealthDTOs.Handle dto) {
        service.handle(id, dto.getMeasure());
        return R.ok();
    }

    // ---------------- 危害因素和岗位 ----------------

    @SaCheckPermission(HealthService.PERM_LIST)
    @Operation(summary = "危害因素目录；enabled=true 只要启用的")
    @GetMapping("/factor/list")
    public R<List<OhFactor>> factors(@RequestParam(required = false) Boolean enabled) {
        return R.ok(service.factors(enabled));
    }

    @OperLog(module = "职业健康", action = "新增危害因素")
    @SaCheckPermission(HealthService.PERM_EDIT)
    @Operation(summary = "新增危害因素")
    @PostMapping("/factor")
    public R<Long> createFactor(@Valid @RequestBody HealthDTOs.FactorSave dto) {
        return R.ok(service.saveFactor(null, dto));
    }

    @OperLog(module = "职业健康", action = "修改危害因素")
    @SaCheckPermission(HealthService.PERM_EDIT)
    @Operation(summary = "修改危害因素（改体检周期后台账立即按新周期计算）")
    @PutMapping("/factor/{id}")
    public R<Long> updateFactor(@PathVariable Long id, @Valid @RequestBody HealthDTOs.FactorSave dto) {
        return R.ok(service.saveFactor(id, dto));
    }

    @OperLog(module = "职业健康", action = "启用 / 停用危害因素")
    @SaCheckPermission(HealthService.PERM_EDIT)
    @Operation(summary = "启用 / 停用危害因素（有岗位接触时不能停用）")
    @PutMapping("/factor/{id}/status")
    public R<Void> factorStatus(@PathVariable Long id, @RequestParam int status) {
        service.factorStatus(id, status);
        return R.ok();
    }

    @SaCheckPermission(HealthService.PERM_EDIT)
    @Operation(summary = "岗位接触的危害因素（岗位在【劳保用品 → 岗位标准】里维护）")
    @GetMapping("/post/list")
    public R<List<HealthVOs.PostFactors>> posts() {
        return R.ok(service.postFactors());
    }

    @OperLog(module = "职业健康", action = "设置岗位危害因素")
    @SaCheckPermission(HealthService.PERM_EDIT)
    @Operation(summary = "设置岗位接触的危害因素（整体替换）")
    @PutMapping("/post/{postId}/factors")
    public R<Void> savePostFactors(@PathVariable Long postId, @RequestBody HealthDTOs.PostFactors dto) {
        service.savePostFactors(postId, dto.getFactorIds());
        return R.ok();
    }
}
