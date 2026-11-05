package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.entity.PpeIssue;
import com.safework.safety.entity.PpeItem;
import com.safework.safety.ppe.PpeDTOs;
import com.safework.safety.ppe.PpeRules;
import com.safework.safety.ppe.PpeService;
import com.safework.safety.ppe.PpeVOs;
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

@Tag(name = "劳保用品", description = "用品目录和库存、岗位发放标准、发放登记、到期台账")
@RestController
@RequestMapping("/safety/ppe")
@RequiredArgsConstructor
public class PpeController {

    private final PpeService service;

    @SaCheckPermission(PpeService.PERM_LIST)
    @Operation(summary = "防护部位字典")
    @GetMapping("/categories")
    public R<Map<String, String>> categories() {
        return R.ok(PpeRules.CATEGORIES);
    }

    // ---------------- 台账 ----------------

    @SaCheckPermission(PpeService.PERM_LIST)
    @Operation(summary = "台账：每人应配的每种用品的状态（安全员看全部，其他人看自己的）；state = MISSING / EXPIRED / DUE_SOON / OK / NEED")
    @GetMapping("/ledger")
    public R<PpeVOs.LedgerPage> ledger(PpeDTOs.LedgerQuery query) {
        return R.ok(service.ledger(query));
    }

    @SaCheckPermission(PpeService.PERM_LIST)
    @Operation(summary = "统计：岗位人数、未发放、已过期、30 天内到期、库存不足的用品")
    @GetMapping("/overview")
    public R<PpeVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @Operation(summary = "我的劳保用品（登录即可）")
    @GetMapping("/mine")
    public R<List<PpeVOs.LedgerRow>> mine() {
        return R.ok(service.mine());
    }

    // ---------------- 发放 ----------------

    @SaCheckPermission(PpeService.PERM_LIST)
    @Operation(summary = "发放记录（安全员看全部，其他人看自己的）")
    @GetMapping("/issue/page")
    public R<Page<PpeIssue>> issuePage(PpeDTOs.IssueQuery query) {
        return R.ok(service.issuePage(query));
    }

    @OperLog(module = "劳保用品", action = "发放登记")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "发放登记：扣库存（不够整单失败），同种用品旧记录改为已更换，通知领用人")
    @PostMapping("/issue")
    public R<List<Long>> issue(@Valid @RequestBody PpeDTOs.Issue dto) {
        return R.ok(service.issue(dto));
    }

    @OperLog(module = "劳保用品", action = "批量补发")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "批量补发：给岗位上未发放、已过期的人发放；任何一种库存不够就一件都不发")
    @PostMapping("/issue/batch")
    public R<PpeVOs.BatchResult> batch(@RequestParam(required = false) Long postId) {
        return R.ok(service.batchIssue(postId));
    }

    // ---------------- 用品目录 ----------------

    @SaCheckPermission(PpeService.PERM_LIST)
    @Operation(summary = "用品目录（含库存）；enabled=true 只要启用的")
    @GetMapping("/item/list")
    public R<List<PpeItem>> items(@RequestParam(required = false) Boolean enabled) {
        return R.ok(service.items(enabled));
    }

    @OperLog(module = "劳保用品", action = "新增用品")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "新增用品（可以带初始库存）")
    @PostMapping("/item")
    public R<Long> createItem(@Valid @RequestBody PpeDTOs.ItemSave dto) {
        return R.ok(service.saveItem(null, dto));
    }

    @OperLog(module = "劳保用品", action = "修改用品")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "修改用品（不改库存；改了使用期限只影响以后的发放）")
    @PutMapping("/item/{id}")
    public R<Long> updateItem(@PathVariable Long id, @Valid @RequestBody PpeDTOs.ItemSave dto) {
        return R.ok(service.saveItem(id, dto));
    }

    @OperLog(module = "劳保用品", action = "入库")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "入库，返回入库后的库存")
    @PostMapping("/item/{id}/stock-in")
    public R<Integer> stockIn(@PathVariable Long id, @Valid @RequestBody PpeDTOs.StockIn dto) {
        return R.ok(service.stockIn(id, dto.getQuantity()));
    }

    @OperLog(module = "劳保用品", action = "启用 / 停用用品")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "启用 / 停用用品（岗位标准里有它时不能停用）")
    @PutMapping("/item/{id}/status")
    public R<Void> itemStatus(@PathVariable Long id, @RequestParam int status) {
        service.itemStatus(id, status);
        return R.ok();
    }

    // ---------------- 岗位标准 ----------------

    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "岗位列表：人员、发放标准")
    @GetMapping("/post/list")
    public R<List<PpeVOs.Post>> posts() {
        return R.ok(service.posts());
    }

    @OperLog(module = "劳保用品", action = "新建岗位")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "新建岗位（人员 + 发放标准）")
    @PostMapping("/post")
    public R<Long> createPost(@Valid @RequestBody PpeDTOs.PostSave dto) {
        return R.ok(service.savePost(null, dto));
    }

    @OperLog(module = "劳保用品", action = "修改岗位")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "修改岗位：人员和发放标准整体替换，台账立即按新标准计算")
    @PutMapping("/post/{id}")
    public R<Long> updatePost(@PathVariable Long id, @Valid @RequestBody PpeDTOs.PostSave dto) {
        return R.ok(service.savePost(id, dto));
    }

    @OperLog(module = "劳保用品", action = "启用 / 停用岗位")
    @SaCheckPermission(PpeService.PERM_EDIT)
    @Operation(summary = "启用 / 停用岗位（停用的岗位不再计入台账）")
    @PutMapping("/post/{id}/status")
    public R<Void> postStatus(@PathVariable Long id, @RequestParam int status) {
        service.postStatus(id, status);
        return R.ok();
    }
}
