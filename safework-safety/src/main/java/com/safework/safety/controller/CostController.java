package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.cost.CostDTOs;
import com.safework.safety.cost.CostRules;
import com.safework.safety.cost.CostService;
import com.safework.safety.cost.CostVOs;
import com.safework.safety.entity.CostExpense;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "安全投入台账", description = "安全生产费用：按上年营业收入超额累退计算应提，分类预算，支出台账（凭证附件、关联隐患），使用统计（财资〔2022〕136 号）")
@RestController
@RequestMapping("/safety/cost")
@RequiredArgsConstructor
public class CostController {

    private final CostService service;

    @SaCheckPermission(CostService.PERM_LIST)
    @Operation(summary = "字典：10 个使用类别、提取比例")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("categories", CostRules.CATEGORY_LIST, "tiers", CostRules.TIERS));
    }

    @SaCheckPermission(CostService.PERM_LIST)
    @Operation(summary = "年度报表：应提、结转、可用、已用、余额、每类预算 / 已用、每月累计、提醒")
    @GetMapping("/report")
    public R<CostVOs.Report> report(@RequestParam(required = false) Integer year) {
        return R.ok(service.report(year == null ? LocalDate.now().getYear() : year));
    }

    @SaCheckPermission(CostService.PERM_LIST)
    @Operation(summary = "按营业收入试算应提（编制计划时实时显示计算过程）")
    @GetMapping("/accrue")
    public R<CostRules.Accrual> accrue(@RequestParam java.math.BigDecimal revenue) {
        return R.ok(CostRules.accrue(revenue));
    }

    @SaCheckPermission(CostService.PERM_EDIT)
    @Operation(summary = "编制计划的表单（已有计划，或者带出建议的上年结转）")
    @GetMapping("/plan")
    public R<CostVOs.PlanForm> planForm(@RequestParam int year) {
        return R.ok(service.planForm(year));
    }

    @OperLog(module = "安全投入", action = "编制年度计划")
    @SaCheckPermission(CostService.PERM_EDIT)
    @Operation(summary = "保存年度计划（应提由系统计算；返回预算超过可用额度的提示）")
    @PutMapping("/plan")
    public R<CostVOs.SaveResult> savePlan(@Valid @RequestBody CostDTOs.PlanSave dto) {
        return R.ok(service.savePlan(dto));
    }

    @SaCheckPermission(CostService.PERM_LIST)
    @Operation(summary = "支出台账（分页，带筛选结果的有效金额合计）")
    @GetMapping("/expense/page")
    public R<CostVOs.ExpensePage> expenses(CostDTOs.ExpenseQuery query) {
        return R.ok(service.expenses(query));
    }

    @SaCheckPermission(CostService.PERM_LIST)
    @Operation(summary = "导出支出台账（Excel，和列表同样的筛选条件）")
    @GetMapping("/expense/export")
    public void export(CostDTOs.ExpenseQuery query, HttpServletResponse response) {
        service.export(query, response);
    }

    @SaCheckPermission(CostService.PERM_LIST)
    @Operation(summary = "支出详情")
    @GetMapping("/expense/{id}")
    public R<CostExpense> expense(@PathVariable Long id) {
        return R.ok(service.expense(id));
    }

    @OperLog(module = "安全投入", action = "登记支出")
    @SaCheckPermission(CostService.PERM_EDIT)
    @Operation(summary = "登记支出（返回超预算提示）")
    @PostMapping("/expense")
    public R<CostVOs.SaveResult> create(@Valid @RequestBody CostDTOs.ExpenseSave dto) {
        return R.ok(service.saveExpense(null, dto));
    }

    @OperLog(module = "安全投入", action = "修改支出")
    @SaCheckPermission(CostService.PERM_EDIT)
    @Operation(summary = "修改支出（作废的不能改）")
    @PutMapping("/expense/{id}")
    public R<CostVOs.SaveResult> update(@PathVariable Long id, @Valid @RequestBody CostDTOs.ExpenseSave dto) {
        return R.ok(service.saveExpense(id, dto));
    }

    @OperLog(module = "安全投入", action = "作废支出")
    @SaCheckPermission(CostService.PERM_EDIT)
    @Operation(summary = "作废（不删除，留痕）")
    @PutMapping("/expense/{id}/void")
    public R<Void> voidExpense(@PathVariable Long id, @Valid @RequestBody CostDTOs.VoidReq dto) {
        service.voidExpense(id, dto.getReason());
        return R.ok();
    }
}
