package com.safework.ticket.approval;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.system.dto.UserOptionVO;
import com.safework.ticket.enums.TicketType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "作业票审批流程", description = "按作业类型配置多级审批节点（或签 / 会签）")
@RestController
@RequestMapping("/ticket/flow")
@RequiredArgsConstructor
public class TicketFlowController {

    static final String PERM = "ticket:flow:edit";

    private final ApprovalService approvalService;

    @SaCheckPermission(PERM)
    @Operation(summary = "三种作业类型的审批流程（含每个节点现在的审批人）")
    @GetMapping
    public R<List<FlowVO>> list() {
        return R.ok(approvalService.flows());
    }

    @SaCheckPermission(PERM)
    @Operation(summary = "可以设为审批人的员工（有作业票审批权限）")
    @GetMapping("/candidates")
    public R<List<UserOptionVO>> candidates() {
        return R.ok(approvalService.candidates());
    }

    @OperLog(module = "作业票审批流程", action = "修改流程")
    @SaCheckPermission(PERM)
    @Operation(summary = "保存某种作业类型的审批流程（只影响之后提交的作业票）")
    @PutMapping("/{type}")
    public R<FlowVO> save(@PathVariable TicketType type, @RequestBody List<FlowNode> nodes) {
        return R.ok(approvalService.save(type, nodes));
    }

    @OperLog(module = "作业票审批流程", action = "恢复默认")
    @SaCheckPermission(PERM)
    @Operation(summary = "恢复默认流程（单节点，有审批权限的人或签）")
    @DeleteMapping("/{type}")
    public R<Void> reset(@PathVariable TicketType type) {
        approvalService.reset(type);
        return R.ok();
    }
}
