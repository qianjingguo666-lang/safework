package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.chem.ChemDTOs;
import com.safework.safety.chem.ChemRules;
import com.safework.safety.chem.ChemService;
import com.safework.safety.chem.ChemVOs;
import com.safework.safety.entity.ChemItem;
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

import java.util.List;
import java.util.Map;

@Tag(name = "危险化学品", description = "危化品目录与 MSDS、存储单元、出入库流水、重大危险源辨识与分级（GB 18218）")
@RestController
@RequestMapping("/safety/chem")
@RequiredArgsConstructor
public class ChemController {

    private final ChemService service;

    @SaCheckPermission(ChemService.PERM_LIST)
    @Operation(summary = "字典：危险类别、单元类型、出入库类型、重大危险源等级")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("categories", ChemRules.CATEGORIES, "storeTypes", ChemRules.STORE_TYPES,
                "flowTypes", ChemRules.FLOW_TYPES, "levels", ChemRules.LEVELS));
    }

    @SaCheckPermission(ChemService.PERM_LIST)
    @Operation(summary = "统计：危化品种数、单元数、重大危险源（按等级）、超储、缺 MSDS")
    @GetMapping("/overview")
    public R<ChemVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    // ---------------- 目录 ----------------

    @SaCheckPermission(ChemService.PERM_LIST)
    @Operation(summary = "危化品目录（含存放单元数、总库存、有没有 MSDS）")
    @GetMapping("/item/list")
    public R<List<ChemVOs.ItemRow>> items(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false, defaultValue = "false") boolean enabled) {
        return R.ok(service.items(keyword, enabled));
    }

    @SaCheckPermission(ChemService.PERM_LIST)
    @Operation(summary = "危化品详情")
    @GetMapping("/item/{id}")
    public R<ChemItem> item(@PathVariable Long id) {
        return R.ok(service.item(id));
    }

    @OperLog(module = "危险化学品", action = "新增危化品")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "新增危化品（CAS 号校验位、不能重复）")
    @PostMapping("/item")
    public R<Long> createItem(@Valid @RequestBody ChemDTOs.ItemSave dto) {
        return R.ok(service.saveItem(null, dto));
    }

    @OperLog(module = "危险化学品", action = "修改危化品")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "修改危化品")
    @PutMapping("/item/{id}")
    public R<Long> updateItem(@PathVariable Long id, @Valid @RequestBody ChemDTOs.ItemSave dto) {
        return R.ok(service.saveItem(id, dto));
    }

    @OperLog(module = "危险化学品", action = "启用 / 停用危化品")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "启用 / 停用（有库存不能停用）")
    @PutMapping("/item/{id}/status")
    public R<Void> itemStatus(@PathVariable Long id, @RequestParam int status) {
        service.itemStatus(id, status);
        return R.ok();
    }

    // ---------------- 存储单元 ----------------

    @SaCheckPermission(ChemService.PERM_LIST)
    @Operation(summary = "存储单元列表，每个单元带重大危险源辨识结果")
    @GetMapping("/store/list")
    public R<List<ChemVOs.Store>> stores() {
        return R.ok(service.stores());
    }

    @SaCheckPermission(ChemService.PERM_LIST)
    @Operation(summary = "存储单元详情")
    @GetMapping("/store/{id}")
    public R<ChemVOs.Store> store(@PathVariable Long id) {
        return R.ok(service.store(id));
    }

    @Operation(summary = "我负责的单元（手机端出入库）；管理员看全部")
    @GetMapping("/store/mine")
    public R<List<ChemVOs.Store>> myStores() {
        return R.ok(service.myStores());
    }

    @OperLog(module = "危险化学品", action = "新增存储单元")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "新增存储单元")
    @PostMapping("/store")
    public R<Long> createStore(@Valid @RequestBody ChemDTOs.StoreSave dto) {
        return R.ok(service.saveStore(null, dto));
    }

    @OperLog(module = "危险化学品", action = "修改存储单元")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "修改存储单元")
    @PutMapping("/store/{id}")
    public R<Long> updateStore(@PathVariable Long id, @Valid @RequestBody ChemDTOs.StoreSave dto) {
        return R.ok(service.saveStore(id, dto));
    }

    @OperLog(module = "危险化学品", action = "启用 / 停用存储单元")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "启用 / 停用存储单元（有库存不能停用）")
    @PutMapping("/store/{id}/status")
    public R<Void> storeStatus(@PathVariable Long id, @RequestParam int status) {
        service.storeStatus(id, status);
        return R.ok();
    }

    @OperLog(module = "危险化学品", action = "设置存放的危化品")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "单元里新增一种危化品，或修改设计最大储量")
    @PostMapping("/store/{id}/stock")
    public R<Long> saveStock(@PathVariable Long id, @Valid @RequestBody ChemDTOs.StockSave dto) {
        return R.ok(service.saveStock(id, dto));
    }

    @OperLog(module = "危险化学品", action = "去掉存放的危化品")
    @SaCheckPermission(ChemService.PERM_EDIT)
    @Operation(summary = "去掉一种危化品（库存必须为 0）")
    @DeleteMapping("/stock/{stockId}")
    public R<Void> removeStock(@PathVariable Long stockId) {
        service.removeStock(stockId);
        return R.ok();
    }

    // ---------------- 出入库 ----------------

    @OperLog(module = "危险化学品", action = "出入库")
    @Operation(summary = "入库 / 出库 / 盘点（单元负责人或管理员）")
    @PostMapping("/stock/{stockId}/flow")
    public R<ChemVOs.FlowResult> flow(@PathVariable Long stockId, @Valid @RequestBody ChemDTOs.Flow dto) {
        return R.ok(service.flow(stockId, dto));
    }

    @SaCheckPermission(ChemService.PERM_LIST)
    @Operation(summary = "出入库流水")
    @GetMapping("/flow/page")
    public R<ChemVOs.FlowPage> flows(ChemDTOs.FlowQuery query) {
        return R.ok(service.flows(query));
    }
}
