package com.safework.ticket.visit;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.ticket.entity.VisitPass;
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

import java.util.List;
import java.util.Map;

@Tag(name = "外来人员与车辆入厂", description = "预约、危化品车辆审批和证件核验、入厂安全须知手写签名、进出厂登记、在厂清单")
@RestController
@RequestMapping("/safety/visit")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService service;

    @Operation(summary = "字典：类型、状态、入厂安全须知")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("types", VisitRules.TYPES, "status", VisitRules.STATUS, "notice", VisitRules.NOTICE));
    }

    @OperLog(module = "外来人员入厂", action = "预约")
    @Operation(summary = "预约（员工替来访人登记；危化品车辆要审批）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody VisitDTOs.Create dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "预约列表（门岗、审批人看全部，其他人看自己登记或接待的）")
    @GetMapping("/page")
    public R<VisitVOs.Page> page(VisitDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @Operation(summary = "详情：危化品车辆检查结果、入厂安全须知、签名")
    @GetMapping("/{id}")
    public R<VisitVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "外来人员入厂", action = "取消预约")
    @Operation(summary = "取消（登记人、接待人或门岗）")
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return R.ok();
    }

    @SaCheckPermission(VisitService.PERM_LIST)
    @Operation(summary = "概况：今日预约、在厂人数、危化品车辆在厂、待审批、超时未离厂")
    @GetMapping("/overview")
    public R<VisitVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @OperLog(module = "外来人员入厂", action = "审批通过")
    @SaCheckPermission(VisitService.PERM_APPROVE)
    @Operation(summary = "危化品车辆审批通过（不能审批自己登记的）")
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        service.approve(id);
        return R.ok();
    }

    @OperLog(module = "外来人员入厂", action = "驳回")
    @SaCheckPermission(VisitService.PERM_APPROVE)
    @Operation(summary = "驳回")
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody VisitDTOs.Reason dto) {
        service.reject(id, dto.getReason());
        return R.ok();
    }

    @SaCheckPermission(VisitService.PERM_GATE)
    @Operation(summary = "门岗：今天要来的和在厂的")
    @GetMapping("/gate")
    public R<List<VisitVOs.Row>> gate() {
        return R.ok(service.gate());
    }

    @SaCheckPermission(VisitService.PERM_GATE)
    @Operation(summary = "在厂清单（应急清点）")
    @GetMapping("/onsite")
    public R<List<VisitPass>> onsite() {
        return R.ok(service.onsite());
    }

    @OperLog(module = "外来人员入厂", action = "入厂登记")
    @SaCheckPermission(VisitService.PERM_GATE)
    @Operation(summary = "入厂：当天、危化品车辆证件有效、来访人签了入厂安全须知")
    @PostMapping("/{id}/enter")
    public R<VisitVOs.EnterResult> enter(@PathVariable Long id, @Valid @RequestBody VisitDTOs.Enter dto) {
        return R.ok(service.enter(id, dto.getSignImage()));
    }

    @OperLog(module = "外来人员入厂", action = "离厂登记")
    @SaCheckPermission(VisitService.PERM_GATE)
    @Operation(summary = "离厂")
    @PutMapping("/{id}/leave")
    public R<Void> leave(@PathVariable Long id) {
        service.leave(id);
        return R.ok();
    }
}
