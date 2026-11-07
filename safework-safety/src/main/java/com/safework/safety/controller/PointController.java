package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.entity.PointRedeem;
import com.safework.safety.point.PointDTOs;
import com.safework.safety.point.PointRules;
import com.safework.safety.point.PointService;
import com.safework.safety.point.PointVOs;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "隐患随手拍积分", description = "隐患被确认、按期整改自动加积分，排行榜，积分兑换奖品，发放金额记入安全投入")
@RestController
@RequestMapping("/safety/point")
@RequiredArgsConstructor
public class PointController {

    private final PointService service;

    // ---------------- 员工（登录即可）----------------

    @Operation(summary = "积分规则和流水类型")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("sourceTypes", PointRules.SOURCE_TYPES, "reportGeneral", PointRules.REPORT_GENERAL,
                "reportMajor", PointRules.REPORT_MAJOR, "rectifyOnTime", PointRules.RECTIFY_ON_TIME));
    }

    @Operation(summary = "我的积分：余额、累计、本月得分和名次、流水、兑换记录")
    @GetMapping("/mine")
    public R<PointVOs.Mine> mine() {
        return R.ok(service.mine());
    }

    @Operation(summary = "排行榜：period = MONTH / YEAR")
    @GetMapping("/ranking")
    public R<PointVOs.Ranking> ranking(@RequestParam(defaultValue = "MONTH") String period, @RequestParam(required = false) Integer year,
                                       @RequestParam(required = false) Integer month) {
        return R.ok(service.ranking(period, year, month));
    }

    @Operation(summary = "可兑换的奖品（带“我能不能兑换”）")
    @GetMapping("/rewards")
    public R<List<PointVOs.RewardRow>> rewards() {
        return R.ok(service.rewards(true));
    }

    @OperLog(module = "隐患随手拍积分", action = "兑换奖品")
    @Operation(summary = "兑换奖品（扣积分、扣库存，等管理员发放）")
    @PostMapping("/rewards/{id}/redeem")
    public R<Long> redeem(@PathVariable Long id) {
        return R.ok(service.redeem(id));
    }

    // ---------------- 管理 ----------------

    @SaCheckPermission(PointService.PERM_LIST)
    @Operation(summary = "今年概况：发出积分、确认隐患、参与人数、待发放、已发放金额")
    @GetMapping("/overview")
    public R<PointVOs.Overview> overview() {
        return R.ok(service.overview(LocalDate.now().getYear()));
    }

    @SaCheckPermission(PointService.PERM_LIST)
    @Operation(summary = "积分流水")
    @GetMapping("/records")
    public R<PointVOs.RecordPage> records(PointDTOs.RecordQuery query) {
        return R.ok(service.records(query));
    }

    @SaCheckPermission(PointService.PERM_LIST)
    @Operation(summary = "全部奖品（含下架的）")
    @GetMapping("/rewards/all")
    public R<List<PointVOs.RewardRow>> allRewards() {
        return R.ok(service.rewards(false));
    }

    @SaCheckPermission(PointService.PERM_LIST)
    @Operation(summary = "兑换记录（status 不传查全部）")
    @GetMapping("/redeems")
    public R<List<PointRedeem>> redeems(@RequestParam(required = false) String status) {
        return R.ok(service.redeems(status));
    }

    @OperLog(module = "隐患随手拍积分", action = "手工调整积分")
    @SaCheckPermission(PointService.PERM_EDIT)
    @Operation(summary = "手工加减积分（必须写原因）")
    @PostMapping("/adjust")
    public R<Void> adjust(@Valid @RequestBody PointDTOs.Adjust dto) {
        service.adjust(dto);
        return R.ok();
    }

    @OperLog(module = "隐患随手拍积分", action = "新增奖品")
    @SaCheckPermission(PointService.PERM_EDIT)
    @Operation(summary = "新增奖品")
    @PostMapping("/rewards")
    public R<Long> createReward(@Valid @RequestBody PointDTOs.RewardSave dto) {
        return R.ok(service.saveReward(null, dto));
    }

    @OperLog(module = "隐患随手拍积分", action = "修改奖品")
    @SaCheckPermission(PointService.PERM_EDIT)
    @Operation(summary = "修改奖品")
    @PutMapping("/rewards/{id}")
    public R<Long> updateReward(@PathVariable Long id, @Valid @RequestBody PointDTOs.RewardSave dto) {
        return R.ok(service.saveReward(id, dto));
    }

    @OperLog(module = "隐患随手拍积分", action = "上架 / 下架奖品")
    @SaCheckPermission(PointService.PERM_EDIT)
    @Operation(summary = "上架 / 下架")
    @PutMapping("/rewards/{id}/status")
    public R<Void> rewardStatus(@PathVariable Long id, @RequestParam int status) {
        service.rewardStatus(id, status);
        return R.ok();
    }

    @OperLog(module = "隐患随手拍积分", action = "发放 / 退回兑换")
    @SaCheckPermission(PointService.PERM_EDIT)
    @Operation(summary = "发放（金额记入安全投入）或退回（积分和库存还回去）")
    @PutMapping("/redeems/{id}/handle")
    public R<Void> handle(@PathVariable Long id, @Valid @RequestBody PointDTOs.Handle dto) {
        service.handle(id, dto);
        return R.ok();
    }
}
