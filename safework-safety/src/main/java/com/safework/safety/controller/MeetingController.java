package com.safework.safety.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.safety.meeting.MeetingDTOs;
import com.safework.safety.meeting.MeetingRules;
import com.safework.safety.meeting.MeetingService;
import com.safework.safety.meeting.MeetingVOs;
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

@Tag(name = "班前会", description = "班组、班前会（安全要点、点名、身体精神状态）、成员确认、开展率")
@RestController
@RequestMapping("/safety/meeting")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService service;

    @Operation(summary = "字典：班次、出勤、身体精神状态")
    @GetMapping("/options")
    public R<Map<String, Object>> options() {
        return R.ok(Map.<String, Object>of("shifts", MeetingRules.SHIFTS, "attends", MeetingRules.ATTENDS, "conditions", MeetingRules.CONDITIONS));
    }

    @SaCheckPermission(MeetingService.PERM_LIST)
    @Operation(summary = "统计：班组数、今天开了几个、今天状态不好不能上岗的人数、30 天开展率、今天没确认的人数")
    @GetMapping("/overview")
    public R<MeetingVOs.Overview> overview() {
        return R.ok(service.overview());
    }

    @SaCheckPermission(MeetingService.PERM_LIST)
    @Operation(summary = "班组（含成员、最近 30 天开展情况）")
    @GetMapping("/team/list")
    public R<List<MeetingVOs.Team>> teams() {
        return R.ok(service.teams());
    }

    @OperLog(module = "班前会", action = "新增班组")
    @SaCheckPermission(MeetingService.PERM_EDIT)
    @Operation(summary = "新增班组")
    @PostMapping("/team")
    public R<Long> createTeam(@Valid @RequestBody MeetingDTOs.TeamSave dto) {
        return R.ok(service.saveTeam(null, dto));
    }

    @OperLog(module = "班前会", action = "修改班组")
    @SaCheckPermission(MeetingService.PERM_EDIT)
    @Operation(summary = "修改班组（班组长、成员）")
    @PutMapping("/team/{id}")
    public R<Long> updateTeam(@PathVariable Long id, @Valid @RequestBody MeetingDTOs.TeamSave dto) {
        return R.ok(service.saveTeam(id, dto));
    }

    @OperLog(module = "班前会", action = "启用 / 停用班组")
    @SaCheckPermission(MeetingService.PERM_EDIT)
    @Operation(summary = "启用 / 停用班组")
    @PutMapping("/team/{id}/status")
    public R<Void> teamStatus(@PathVariable Long id, @RequestParam int status) {
        service.teamStatus(id, status);
        return R.ok();
    }

    @SaCheckPermission(MeetingService.PERM_LIST)
    @Operation(summary = "班前会记录")
    @GetMapping("/page")
    public R<MeetingVOs.Page> page(MeetingDTOs.Query query) {
        return R.ok(service.page(query));
    }

    @Operation(summary = "班前会详情（有菜单权限的人或参加的人）")
    @GetMapping("/{id}")
    public R<MeetingVOs.Detail> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @Operation(summary = "我当班组长的班组、我要确认的班前会（手机端）")
    @GetMapping("/mine")
    public R<MeetingVOs.Mine> mine() {
        return R.ok(service.mine());
    }

    @OperLog(module = "班前会", action = "开班前会")
    @Operation(summary = "开班前会：安全要点 + 点名（班组长或安全管理员）")
    @PostMapping
    public R<Long> hold(@Valid @RequestBody MeetingDTOs.Save dto) {
        return R.ok(service.hold(dto));
    }

    @Operation(summary = "成员确认已参加、清楚今天的安全要点")
    @PostMapping("/{id}/confirm")
    public R<Void> confirm(@PathVariable Long id) {
        service.confirm(id);
        return R.ok();
    }
}
