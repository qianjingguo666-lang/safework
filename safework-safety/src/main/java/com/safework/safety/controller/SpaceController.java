package com.safework.safety.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.dto.SpaceOptionVO;
import com.safework.safety.dto.SpaceQueryDTO;
import com.safework.safety.dto.SpaceSaveDTO;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.excel.SafetyExcelService;
import com.safework.safety.service.SpaceService;
import com.safework.system.excel.ImportResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.safework.system.dto.StatusDTO;
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

@Tag(name = "受限空间台账", description = "台账维护、二维码、导入导出")
@RestController
@RequestMapping("/safety/space")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final SafetyExcelService excelService;

    @SaCheckPermission("safety:space:import")
    @Operation(summary = "导出受限空间台账")
    @GetMapping("/export")
    public void export(SpaceQueryDTO query, HttpServletResponse response) {
        excelService.exportSpaces(query, response);
    }

    @SaCheckPermission("safety:space:import")
    @Operation(summary = "下载导入模板")
    @GetMapping("/import-template")
    public void template(HttpServletResponse response) {
        excelService.spaceTemplate(response);
    }

    /** 批量导入：返回成功条数和每一行的错误 */
    @OperLog(module = "受限空间", action = "导入台账")
    @SaCheckPermission("safety:space:import")
    @Operation(summary = "批量导入")
    @PostMapping("/import")
    public R<ImportResult> importSpaces(@RequestParam("file") MultipartFile file) {
        return R.ok(excelService.importSpaces(file));
    }

    @SaCheckPermission("safety:space:list")
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<ConfinedSpace>> page(SpaceQueryDTO query) {
        return R.ok(spaceService.page(query));
    }

    /** 下拉选择（新建作业票、上报隐患时用）：登录即可 */
    @Operation(summary = "下拉选择（新建作业票、上报隐患时用）")
    @GetMapping("/options")
    public R<List<SpaceOptionVO>> options() {
        return R.ok(spaceService.options());
    }

    @SaCheckPermission("safety:space:list")
    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<ConfinedSpace> detail(@PathVariable Long id) {
        return R.ok(spaceService.getById(id));
    }

    /** 二维码令牌单独接口返回，并且需要修改权限：列表不暴露令牌，防止普通用户批量导出 */
    @SaCheckPermission("safety:space:edit")
    @Operation(summary = "二维码令牌单独接口返回，并且需要修改权限")
    @GetMapping("/{id}/qr-token")
    public R<String> qrToken(@PathVariable Long id) {
        return R.ok(spaceService.getById(id).getQrToken());
    }

    @OperLog(module = "受限空间", action = "新增空间")
    @SaCheckPermission("safety:space:add")
    @Operation(summary = "新增空间")
    @PostMapping
    public R<Long> create(@Valid @RequestBody SpaceSaveDTO dto) {
        return R.ok(spaceService.create(dto));
    }

    @OperLog(module = "受限空间", action = "修改空间")
    @SaCheckPermission("safety:space:edit")
    @Operation(summary = "修改空间")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SpaceSaveDTO dto) {
        spaceService.update(id, dto);
        return R.ok();
    }

    @OperLog(module = "受限空间", action = "启用/停用")
    @SaCheckPermission("safety:space:edit")
    @Operation(summary = "启用/停用")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        spaceService.changeStatus(id, dto.getStatus());
        return R.ok();
    }

    @OperLog(module = "受限空间", action = "重置二维码")
    @SaCheckPermission("safety:space:edit")
    @Operation(summary = "重置二维码")
    @PostMapping("/{id}/qr-token/reset")
    public R<Void> resetQrToken(@PathVariable Long id) {
        spaceService.resetQrToken(id);
        return R.ok();
    }
}
