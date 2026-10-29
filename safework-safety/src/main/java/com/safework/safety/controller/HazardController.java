package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.dto.HazardActionDTO;
import com.safework.safety.dto.HazardDetailVO;
import com.safework.safety.dto.HazardQueryDTO;
import com.safework.safety.dto.HazardReportDTO;
import com.safework.safety.entity.Hazard;
import com.safework.safety.excel.SafetyExcelService;
import com.safework.safety.service.HazardService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 隐患接口。整改人可能是任何部门的员工，所以“提交整改”和“详情”只要求登录，
 * 是不是本人负责的隐患在 Service 里判断。
 */
@RestController
@RequestMapping("/safety/hazard")
@RequiredArgsConstructor
public class HazardController {

    private final HazardService hazardService;
    private final SafetyExcelService excelService;

    /** 导出隐患台账（筛选条件和列表一致） */
    @SaCheckPermission("safety:hazard:export")
    @GetMapping("/export")
    public void export(HazardQueryDTO query, HttpServletResponse response) {
        excelService.exportHazards(query, response);
    }

    @SaCheckPermission("safety:hazard:list")
    @GetMapping("/page")
    public R<Page<Hazard>> page(HazardQueryDTO query) {
        return R.ok(hazardService.page(query));
    }

    @GetMapping("/{id}")
    public R<HazardDetailVO> detail(@PathVariable Long id) {
        return R.ok(hazardService.detail(id));
    }

    @OperLog(module = "隐患排查", action = "上报隐患")
    @SaCheckPermission("safety:hazard:report")
    @PostMapping
    public R<Long> report(@Valid @RequestBody HazardReportDTO dto) {
        return R.ok(hazardService.report(dto));
    }

    @OperLog(module = "隐患排查", action = "指派整改")
    @SaCheckPermission(HazardService.PERM_HANDLE)
    @PostMapping("/{id}/assign")
    public R<Void> assign(@PathVariable Long id, @RequestBody(required = false) HazardActionDTO dto) {
        hazardService.assign(id, orEmpty(dto));
        return R.ok();
    }

    @OperLog(module = "隐患排查", action = "撤销")
    @SaCheckPermission(HazardService.PERM_HANDLE)
    @PostMapping("/{id}/revoke")
    public R<Void> revoke(@PathVariable Long id, @RequestBody(required = false) HazardActionDTO dto) {
        hazardService.revoke(id, orEmpty(dto));
        return R.ok();
    }

    @OperLog(module = "隐患排查", action = "提交整改")
    @PostMapping("/{id}/rectify")
    public R<Void> rectify(@PathVariable Long id, @RequestBody(required = false) HazardActionDTO dto) {
        hazardService.rectify(id, orEmpty(dto));
        return R.ok();
    }

    @OperLog(module = "隐患排查", action = "验收通过")
    @SaCheckPermission(HazardService.PERM_HANDLE)
    @PostMapping("/{id}/verify-pass")
    public R<Void> verifyPass(@PathVariable Long id, @RequestBody(required = false) HazardActionDTO dto) {
        hazardService.verifyPass(id, orEmpty(dto));
        return R.ok();
    }

    @OperLog(module = "隐患排查", action = "验收不通过")
    @SaCheckPermission(HazardService.PERM_HANDLE)
    @PostMapping("/{id}/verify-fail")
    public R<Void> verifyFail(@PathVariable Long id, @RequestBody(required = false) HazardActionDTO dto) {
        hazardService.verifyFail(id, orEmpty(dto));
        return R.ok();
    }

    private static HazardActionDTO orEmpty(HazardActionDTO dto) {
        return dto == null ? new HazardActionDTO() : dto;
    }
}
