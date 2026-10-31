package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.common.exception.BizException;
import com.safework.safety.contractor.ContractorActionDTO;
import com.safework.safety.contractor.ContractorDetailVO;
import com.safework.safety.contractor.ContractorOptionVO;
import com.safework.safety.contractor.ContractorOverviewVO;
import com.safework.safety.contractor.ContractorQueryDTO;
import com.safework.safety.contractor.ContractorRules;
import com.safework.safety.contractor.ContractorSaveDTO;
import com.safework.safety.contractor.ContractorService;
import com.safework.safety.contractor.ViolationResultVO;
import com.safework.safety.contractor.ViolationSaveDTO;
import com.safework.safety.contractor.WorkerSaveDTO;
import com.safework.safety.entity.Contractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "承包商管理", description = "准入审核、人员登记、违章扣分、黑名单")
@RestController
@RequestMapping("/safety/contractor")
@RequiredArgsConstructor
public class ContractorController {

    private final ContractorService service;

    @SaCheckPermission(ContractorService.PERM_LIST)
    @Operation(summary = "承包商分页（含近 12 个月安全评分、在场人数、到期提示）")
    @GetMapping("/page")
    public R<Page<Contractor>> page(ContractorQueryDTO query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(ContractorService.PERM_LIST)
    @Operation(summary = "统计：合格、待审核、黑名单、资质保险快到期、人员培训快到期")
    @GetMapping("/overview")
    public R<ContractorOverviewVO> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission("ticket:add")
    @Operation(summary = "新建作业票时选择承包商和作业人员（只列审核合格的单位）")
    @GetMapping("/options")
    public R<List<ContractorOptionVO>> options() {
        return R.ok(service.options());
    }

    @SaCheckPermission(ContractorService.PERM_LIST)
    @Operation(summary = "详情：单位信息、人员、违章记录、可执行的状态操作")
    @GetMapping("/{id}")
    public R<ContractorDetailVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "承包商管理", action = "登记承包商")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "登记承包商（状态为待审核）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ContractorSaveDTO dto) {
        return R.ok(service.save(null, dto));
    }

    @OperLog(module = "承包商管理", action = "修改承包商")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "修改承包商信息")
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody ContractorSaveDTO dto) {
        return R.ok(service.save(id, dto));
    }

    @OperLog(module = "承包商管理", action = "审核 / 拉黑")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "状态操作：approve 审核通过 / reject 驳回 / resubmit 重新提交 / blacklist 拉黑 / lift 解除黑名单（驳回、拉黑、解除要写原因）")
    @PostMapping(value = "/{id}/status/{action}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<Void> act(@PathVariable Long id, @PathVariable String action, @Valid @RequestBody ContractorActionDTO dto) {
        service.act(id, parseAction(action), dto);
        return R.ok();
    }

    /**
     * 不带请求体的状态操作（审核通过、重新提交不需要写意见）。
     * 注意上面的 @RequestBody 必须是必填：可选请求体遇到空请求时 consumes 条件不生效，两个接口会冲突。
     */
    @OperLog(module = "承包商管理", action = "审核 / 拉黑")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "状态操作（不带请求体）")
    @PostMapping("/{id}/status/{action}")
    public R<Void> actWithoutBody(@PathVariable Long id, @PathVariable String action) {
        service.act(id, parseAction(action), new ContractorActionDTO());
        return R.ok();
    }

    private static ContractorRules.Action parseAction(String action) {
        try {
            return ContractorRules.Action.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("不支持的操作：" + action);
        }
    }

    @OperLog(module = "承包商管理", action = "登记人员")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "登记承包商人员（入厂培训、特种作业证）")
    @PostMapping("/{id}/worker")
    public R<Long> addWorker(@PathVariable Long id, @Valid @RequestBody WorkerSaveDTO dto) {
        return R.ok(service.saveWorker(id, null, dto));
    }

    @OperLog(module = "承包商管理", action = "修改人员")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "修改承包商人员（复训后更新培训日期）")
    @PutMapping("/{id}/worker/{workerId}")
    public R<Long> updateWorker(@PathVariable Long id, @PathVariable Long workerId, @Valid @RequestBody WorkerSaveDTO dto) {
        return R.ok(service.saveWorker(id, workerId, dto));
    }

    @OperLog(module = "承包商管理", action = "人员离场 / 进场")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "人员离场（status=0）/ 重新进场（status=1）")
    @PutMapping("/{id}/worker/{workerId}/status")
    public R<Void> workerStatus(@PathVariable Long id, @PathVariable Long workerId, @RequestBody Map<String, Integer> body) {
        service.changeWorkerStatus(id, workerId, body.get("status"));
        return R.ok();
    }

    @OperLog(module = "承包商管理", action = "记录违章")
    @SaCheckPermission(ContractorService.PERM_EDIT)
    @Operation(summary = "记录违章并扣分；评分低于 60 分自动列入黑名单")
    @PostMapping("/{id}/violation")
    public R<ViolationResultVO> addViolation(@PathVariable Long id, @Valid @RequestBody ViolationSaveDTO dto) {
        return R.ok(service.addViolation(id, dto));
    }
}
