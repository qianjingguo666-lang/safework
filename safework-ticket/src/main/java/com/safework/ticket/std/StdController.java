package com.safework.ticket.std;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.util.List;
import java.util.Map;

@Tag(name = "安全生产标准化自评", description = "按 GB/T 33000 的 8 个一级要素逐项自评打分，系统从各模块取证给建议扣分，空项不计分，得出等级，扣分项转隐患整改")
@RestController
@RequestMapping("/safety/std")
@RequiredArgsConstructor
public class StdController {

    private final StdService service;

    @SaCheckPermission(StdService.PERM_LIST)
    @Operation(summary = "考评标准：8 个一级要素、27 个考评项目")
    @GetMapping("/standard")
    public R<Map<String, Object>> standard() {
        return R.ok(Map.<String, Object>of("elements", StdRules.ELEMENTS, "items", StdRules.ITEMS));
    }

    @SaCheckPermission(StdService.PERM_LIST)
    @Operation(summary = "自评记录")
    @GetMapping("/list")
    public R<List<StdVOs.ListRow>> list() {
        return R.ok(service.list());
    }

    @SaCheckPermission(StdService.PERM_LIST)
    @Operation(summary = "自评详情：逐项证据和扣分、要素小计、得分等级、和上一次对比")
    @GetMapping("/{id}")
    public R<StdVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @OperLog(module = "标准化自评", action = "新建自评")
    @SaCheckPermission(StdService.PERM_EDIT)
    @Operation(summary = "新建自评（系统自动取证、填入建议扣分；同时只能有一次进行中）")
    @PostMapping
    public R<Long> create(@Valid @RequestBody StdDTOs.Create dto) {
        return R.ok(service.create(dto));
    }

    @OperLog(module = "标准化自评", action = "重新取数")
    @SaCheckPermission(StdService.PERM_EDIT)
    @Operation(summary = "重新取数：更新证据和建议扣分（不改已填的扣分），返回建议有变化的项数")
    @PostMapping("/{id}/refresh")
    public R<Integer> refresh(@PathVariable Long id) {
        return R.ok(service.refresh(id));
    }

    @SaCheckPermission(StdService.PERM_EDIT)
    @Operation(summary = "保存一项：空项、扣分（0.5 分为单位）、扣分说明")
    @PutMapping("/item/{itemId}")
    public R<Void> saveItem(@PathVariable Long itemId, @Valid @RequestBody StdDTOs.ItemSave dto) {
        service.saveItem(itemId, dto);
        return R.ok();
    }

    @OperLog(module = "标准化自评", action = "完成自评")
    @SaCheckPermission(StdService.PERM_EDIT)
    @Operation(summary = "完成自评：扣分项必须有说明，计算得分和等级")
    @PostMapping("/{id}/finish")
    public R<StdVOs.Detail> finish(@PathVariable Long id, @Valid @RequestBody StdDTOs.Finish dto) {
        return R.ok(service.finish(id, dto));
    }

    @OperLog(module = "标准化自评", action = "删除自评")
    @SaCheckPermission(StdService.PERM_EDIT)
    @Operation(summary = "删除进行中的自评")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @OperLog(module = "标准化自评", action = "扣分项转隐患整改")
    @SaCheckPermission(StdService.PERM_EDIT)
    @Operation(summary = "扣分项转隐患整改")
    @PostMapping("/item/{itemId}/hazard")
    public R<StdVOs.HazardResult> toHazard(@PathVariable Long itemId) {
        return R.ok(service.toHazard(itemId));
    }

    @SaCheckPermission(StdService.PERM_LIST)
    @Operation(summary = "导出扣分汇总表（Excel）")
    @GetMapping("/{id}/export")
    public void export(@PathVariable Long id, HttpServletResponse response) {
        service.export(id, response);
    }
}
