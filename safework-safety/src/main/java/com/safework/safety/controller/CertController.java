package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.cert.CertOverviewVO;
import com.safework.safety.cert.CertProblemVO;
import com.safework.safety.cert.CertQueryDTO;
import com.safework.safety.cert.CertSaveDTO;
import com.safework.safety.cert.CertService;
import com.safework.safety.cert.RequirementDTO;
import com.safework.safety.entity.CertRequirement;
import com.safework.safety.entity.PersonCert;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "人员资质", description = "证书台账、到期提醒、开票持证要求")
@RestController
@RequestMapping("/safety/cert")
@RequiredArgsConstructor
public class CertController {

    private final CertService service;

    @SaCheckPermission(CertService.PERM_LIST)
    @Operation(summary = "证书分页（安全员看全部，其他人只看自己的）；state = VALID / EXPIRING / EXPIRED")
    @GetMapping("/page")
    public R<Page<PersonCert>> page(CertQueryDTO query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(CertService.PERM_LIST)
    @Operation(summary = "统计：证书数、持证人数、30 天内到期、已过期")
    @GetMapping("/overview")
    public R<CertOverviewVO> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(CertService.PERM_LIST)
    @Operation(summary = "导出人员资质台账")
    @GetMapping("/export")
    public void export(CertQueryDTO query, HttpServletResponse response) {
        service.export(query, response);
    }

    @SaCheckPermission(CertService.PERM_LIST)
    @Operation(summary = "开票持证要求")
    @GetMapping("/requirement")
    public R<List<CertRequirement>> requirements() {
        return R.ok(service.requirements());
    }

    @OperLog(module = "人员资质", action = "修改开票持证要求")
    @SaCheckPermission(CertService.PERM_EDIT)
    @Operation(summary = "整体保存开票持证要求")
    @PutMapping("/requirement")
    public R<Void> saveRequirements(@Valid @RequestBody RequirementDTO dto) {
        service.saveRequirements(dto);
        return R.ok();
    }

    @SaCheckPermission("ticket:add")
    @Operation(summary = "新建作业票时预检：当前用户（作业负责人）和所选监护人的证书是否满足要求")
    @GetMapping("/ticket-check")
    public R<List<CertProblemVO>> ticketCheck(@RequestParam String ticketType,
                                             @RequestParam(required = false) Long guardianId,
                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime planEndTime) {
        return R.ok(service.check(ticketType, StpUtil.getLoginIdAsLong(), guardianId, planEndTime));
    }

    @SaCheckPermission(CertService.PERM_LIST)
    @Operation(summary = "证书详情")
    @GetMapping("/{id}")
    public R<PersonCert> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "人员资质", action = "登记证书")
    @SaCheckPermission(CertService.PERM_EDIT)
    @Operation(summary = "登记证书")
    @PostMapping
    public R<Long> create(@Valid @RequestBody CertSaveDTO dto) {
        return R.ok(service.save(null, dto));
    }

    @OperLog(module = "人员资质", action = "修改证书")
    @SaCheckPermission(CertService.PERM_EDIT)
    @Operation(summary = "修改证书（换证、复审后更新日期）")
    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @Valid @RequestBody CertSaveDTO dto) {
        return R.ok(service.save(id, dto));
    }

    @OperLog(module = "人员资质", action = "删除证书")
    @SaCheckPermission(CertService.PERM_EDIT)
    @Operation(summary = "删除证书")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
