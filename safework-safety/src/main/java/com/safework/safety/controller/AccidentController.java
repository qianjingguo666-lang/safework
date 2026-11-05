package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.accident.AccidentDTOs;
import com.safework.safety.accident.AccidentService;
import com.safework.safety.accident.AccidentVOs;
import com.safework.safety.entity.AccidentMeasure;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "事故调查", description = "事故调查处理“四不放过”：原因分析、整改措施、责任处理、警示教育、结案")
@RestController
@RequestMapping("/safety/accident")
@RequiredArgsConstructor
public class AccidentController {

    private final AccidentService service;

    // ---------------- 查询 ----------------

    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "统计：调查中、超期、未完成 / 逾期的整改措施、今年结案")
    @GetMapping("/overview")
    public R<AccidentVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "事故调查列表（含措施进度、“四不放过”满足几项）")
    @GetMapping("/page")
    public R<AccidentVOs.Page> page(AccidentDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "调查详情：事故快报、原因、措施、责任处理、教育、“四不放过”检查")
    @GetMapping("/{id}")
    public R<AccidentVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @Operation(summary = "我负责的整改措施（整改责任人用，不需要菜单权限）")
    @GetMapping("/measure/mine")
    public R<List<AccidentMeasure>> mine() {
        return R.ok(service.mine());
    }

    // ---------------- 启动调查、调查组 ----------------

    @SaCheckPermission(AccidentService.PERM_EDIT)
    @Operation(summary = "还没有启动调查的事故快报")
    @GetMapping("/candidates")
    public R<List<AccidentVOs.Candidate>> candidates() {
        return R.ok(service.candidates());
    }

    @OperLog(module = "事故调查", action = "启动调查")
    @SaCheckPermission(AccidentService.PERM_EDIT)
    @Operation(summary = "启动调查：指定调查组长；期限不填按等级自动算（一般 30 天，较大及以上 60 天）")
    @PostMapping
    public R<Long> start(@Valid @RequestBody AccidentDTOs.Team dto) {
        return R.ok(service.start(dto));
    }

    @OperLog(module = "事故调查", action = "修改调查组")
    @SaCheckPermission(AccidentService.PERM_EDIT)
    @Operation(summary = "修改调查组长、成员、期限")
    @PutMapping("/{id}/team")
    public R<Void> team(@PathVariable Long id, @Valid @RequestBody AccidentDTOs.Team dto) {
        service.updateTeam(id, dto);
        return R.ok();
    }

    // ---------------- 调查内容（管理员或调查组长，在 service 里校验） ----------------

    @OperLog(module = "事故调查", action = "原因分析")
    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "保存原因分析：直接原因、间接原因、管理原因、经济损失、教训")
    @PutMapping("/{id}/analysis")
    public R<Void> analysis(@PathVariable Long id, @Valid @RequestBody AccidentDTOs.Analysis dto) {
        service.saveAnalysis(id, dto);
        return R.ok();
    }

    @OperLog(module = "事故调查", action = "警示教育")
    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "登记警示教育")
    @PutMapping("/{id}/education")
    public R<Void> education(@PathVariable Long id, @Valid @RequestBody AccidentDTOs.Education dto) {
        service.saveEducation(id, dto);
        return R.ok();
    }

    @OperLog(module = "事故调查", action = "新增整改措施")
    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "新增整改措施（通知责任人）")
    @PostMapping("/{id}/measure")
    public R<Long> addMeasure(@PathVariable Long id, @Valid @RequestBody AccidentDTOs.MeasureSave dto) {
        return R.ok(service.addMeasure(id, dto));
    }

    @OperLog(module = "事故调查", action = "删除整改措施")
    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "删除还没完成的整改措施")
    @DeleteMapping("/measure/{measureId}")
    public R<Void> removeMeasure(@PathVariable Long measureId) {
        service.removeMeasure(measureId);
        return R.ok();
    }

    @OperLog(module = "事故调查", action = "整改措施完成")
    @Operation(summary = "整改责任人填写落实情况（责任人本人或管理员）")
    @PostMapping("/measure/{measureId}/done")
    public R<Void> done(@PathVariable Long measureId, @Valid @RequestBody AccidentDTOs.MeasureDone dto) {
        service.completeMeasure(measureId, dto.getNote());
        return R.ok();
    }

    @OperLog(module = "事故调查", action = "登记责任处理")
    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "登记责任人员处理")
    @PostMapping("/{id}/punish")
    public R<Long> addPunish(@PathVariable Long id, @Valid @RequestBody AccidentDTOs.PunishSave dto) {
        return R.ok(service.addPunish(id, dto));
    }

    @OperLog(module = "事故调查", action = "删除责任处理")
    @SaCheckPermission(AccidentService.PERM_LIST)
    @Operation(summary = "删除责任处理记录")
    @DeleteMapping("/punish/{punishId}")
    public R<Void> removePunish(@PathVariable Long punishId) {
        service.removePunish(punishId);
        return R.ok();
    }

    // ---------------- 结案 ----------------

    @OperLog(module = "事故调查", action = "结案")
    @SaCheckPermission(AccidentService.PERM_EDIT)
    @Operation(summary = "结案（“四不放过”都满足才行），可选把事故案例写进 AI 知识库")
    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id, @RequestBody(required = false) AccidentDTOs.Close dto) {
        service.close(id, dto != null && Boolean.TRUE.equals(dto.getToKnowledge()));
        return R.ok();
    }
}
