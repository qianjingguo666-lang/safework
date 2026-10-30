package com.safework.ticket.approval;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safework.common.exception.BizException;
import com.safework.system.dto.UserOptionVO;
import com.safework.system.entity.SysRole;
import com.safework.system.entity.SysUser;
import com.safework.system.mapper.SysUserMapper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.DeptService;
import com.safework.system.service.RoleService;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketType;
import com.safework.ticket.mapper.ApprovalTaskMapper;
import com.safework.ticket.mapper.TicketFlowMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.ticket.service.TicketNotifier;
import com.safework.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作业票审批流程：按作业类型配置多个审批节点，每个节点或签 / 会签。
 * <pre>
 * 提交：保存流程快照 → 激活第 1 个节点（给节点审批人生成待办、发消息）
 * 同意：节点通过？
 *        ├─ 还有下一个节点 → 激活下一个节点
 *        └─ 最后一个节点   → 作业票状态变为【已批准】（走原来的状态机）
 * 驳回：任何一个节点驳回 → 取消所有待办，作业票变为【已驳回】，申请人修改后重新提交（新的一轮）
 * </pre>
 * 作业票的状态机不变（待审批 = 审批中），多级审批是“待审批”状态内部的细分，对其他模块透明。
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    /** 审批完成后 TicketService 要做的事 */
    public enum Decision {
        /** 会签节点还有人没审批 */
        WAITING,
        /** 节点通过，进入下一个节点 */
        NEXT,
        /** 最后一个节点通过，作业票批准 */
        FINISHED
    }

    private final TicketFlowMapper flowMapper;
    private final ApprovalTaskMapper taskMapper;
    private final WorkTicketMapper ticketMapper;
    private final UserService userService;
    private final SysUserMapper userMapper;
    private final RoleService roleService;
    private final DeptService deptService;
    private final MessageService messageService;
    private final ObjectMapper json = new ObjectMapper();

    // ============================== 流程配置 ==============================

    public List<FlowNode> flowOf(TicketType type) {
        TicketFlow f = flowMapper.selectOne(Wrappers.<TicketFlow>lambdaQuery().eq(TicketFlow::getTicketType, type));
        return f == null ? ApprovalRules.DEFAULT_FLOW : parse(f.getNodes());
    }

    public List<FlowVO> flows() {
        Map<TicketType, TicketFlow> configured = flowMapper.selectList(null).stream()
                .collect(Collectors.toMap(TicketFlow::getTicketType, Function.identity(), (a, b) -> a));
        List<FlowVO> result = new ArrayList<>();
        for (TicketType type : TicketType.values()) {
            TicketFlow f = configured.get(type);
            List<FlowNode> nodes = f == null ? ApprovalRules.DEFAULT_FLOW : parse(f.getNodes());
            result.add(new FlowVO(type, type.getLabel(), f != null, f == null ? null : f.getUpdaterName(), nodes,
                    nodes.stream().map(n -> resolve(n, null).stream().map(SysUser::getNickname).toList()).toList()));
        }
        return result;
    }

    /** 保存流程配置：格式校验 + 角色、人员必须存在 + 每个节点现在至少有一个人能审批 */
    public FlowVO save(TicketType type, List<FlowNode> input) {
        List<FlowNode> nodes = input == null ? List.of() : input.stream().map(n -> n == null ? null : new FlowNode(
                n.name() == null ? null : n.name().trim(), n.approverType(),
                FlowNode.ROLE.equals(n.approverType()) ? n.roleCode() : null,
                FlowNode.USER.equals(n.approverType()) && n.userIds() != null ? n.userIds().stream().distinct().toList() : null,
                n.mode())).toList();
        String error = ApprovalRules.validate(nodes);
        if (error != null) {
            throw new BizException(error);
        }
        Set<String> roleCodes = roleService.list().stream().map(SysRole::getRoleCode).collect(Collectors.toSet());
        for (FlowNode n : nodes) {
            if (FlowNode.ROLE.equals(n.approverType()) && !roleCodes.contains(n.roleCode())) {
                throw new BizException("节点【" + n.name() + "】的角色不存在");
            }
            if (resolve(n, null).isEmpty()) {
                throw new BizException("节点【" + n.name() + "】现在没有人能审批：审批人必须是正常状态、并且有作业票审批权限的员工");
            }
        }
        TicketFlow f = flowMapper.selectOne(Wrappers.<TicketFlow>lambdaQuery().eq(TicketFlow::getTicketType, type));
        if (f == null) {
            f = new TicketFlow();
            f.setTicketType(type);
        }
        f.setNodes(write(nodes));
        Object nickname = StpUtil.getSession().get(com.safework.system.service.AuthService.SESSION_NICKNAME);
        f.setUpdaterName(nickname == null ? null : nickname.toString());
        if (f.getId() == null) {
            flowMapper.insert(f);
        } else {
            flowMapper.updateById(f);
        }
        return flows().stream().filter(v -> v.ticketType() == type).findFirst().orElseThrow();
    }

    /** 恢复默认流程（删除配置） */
    public void reset(TicketType type) {
        flowMapper.delete(Wrappers.<TicketFlow>lambdaQuery().eq(TicketFlow::getTicketType, type));
    }

    /** 可以设为审批人的员工：正常状态、有作业票审批权限 */
    public List<UserOptionVO> candidates() {
        Map<Long, String> deptNames = deptService.nameMap();
        return users(userService.listUserIdsWithPermission(TicketService.PERM_APPROVE)).stream()
                .map(u -> new UserOptionVO(u.getId(), u.getNickname(), deptNames.get(u.getDeptId())))
                .toList();
    }

    /**
     * 节点现在的审批人：在“有审批权限、状态正常”的员工里，按角色或人员筛选；申请人自己不能审批自己的票。
     */
    List<SysUser> resolve(FlowNode n, Long applicantId) {
        Set<Long> allowed = new HashSet<>(userService.listUserIdsWithPermission(TicketService.PERM_APPROVE));
        Collection<Long> ids = switch (n.approverType()) {
            case FlowNode.ROLE -> userService.listByRoleCode(n.roleCode()).stream().map(SysUser::getId).filter(allowed::contains).toList();
            case FlowNode.USER -> n.userIds().stream().filter(allowed::contains).toList();
            default -> allowed;
        };
        Set<Long> result = new LinkedHashSet<>(ids);
        result.remove(applicantId);
        return users(result);
    }

    // ============================== 审批过程 ==============================

    /** 提交前：开始新的一轮，保存流程快照（只改对象，由 TicketService 的状态流转一起保存） */
    public void prepare(WorkTicket t) {
        List<FlowNode> nodes = flowOf(t.getTicketType());
        // 提前检查每个节点都有人能审批（申请人不能审批自己的票），避免审到一半卡住
        for (FlowNode n : nodes) {
            if (resolve(n, t.getApplicantId()).isEmpty()) {
                throw new BizException("审批节点【" + n.name() + "】除了申请人自己没有其他审批人，无法提交，请联系管理员调整审批流程");
            }
        }
        t.setApprovalRound((t.getApprovalRound() == null ? 0 : t.getApprovalRound()) + 1);
        t.setCurrentNode(0);
        t.setFlowSnapshot(write(nodes));
    }

    /** 激活当前节点：给审批人生成待办并通知。没有人能审批时抛异常，整个提交回滚 */
    public void activate(WorkTicket t, SysUser operator) {
        activate(t, operator, true);
    }

    private void activate(WorkTicket t, SysUser operator, boolean notify) {
        List<FlowNode> nodes = snapshot(t);
        int index = t.getCurrentNode();
        FlowNode node = nodes.get(index);
        List<SysUser> approvers = resolve(node, t.getApplicantId());
        if (approvers.isEmpty()) {
            throw new BizException("审批节点【" + node.name() + "】没有可用的审批人，请联系管理员检查审批流程配置");
        }
        for (SysUser u : approvers) {
            ApprovalTask task = new ApprovalTask();
            task.setTicketId(t.getId());
            task.setRoundNo(t.getApprovalRound());
            task.setNodeIndex(index);
            task.setNodeName(node.name());
            task.setMode(node.mode());
            task.setApproverId(u.getId());
            task.setApproverName(u.getNickname());
            task.setStatus(ApprovalRules.PENDING);
            taskMapper.insert(task);
        }
        if (!notify) {
            return;
        }
        String step = nodes.size() > 1 ? "（第 " + (index + 1) + "/" + nodes.size() + " 步：" + node.name()
                + (FlowNode.MODE_ALL.equals(node.mode()) ? "，会签" : "") + "）" : "";
        List<Long> ids = approvers.stream().map(SysUser::getId).filter(id -> !Objects.equals(id, operator.getId())).toList();
        messageService.send(ids, MessageDraft.of(MessageCategory.TODO, TicketNotifier.BIZ_TYPE, t.getId(),
                "待审批：" + t.getTicketNo() + " " + t.getTitle() + step,
                t.getApplicantName() + " 提交了" + t.getTicketType().getLabel() + "，作业地点 " + t.getLocation()
                        + "，请审核安全措施和气体检测数据", TicketNotifier.link(t.getId())));
    }

    /**
     * 同意。调用前 TicketService 已经用 SELECT ... FOR UPDATE 锁住了这张票：
     * 同一张票的审批操作串行执行，或签节点两个人同时点同意也只会推进一次。
     */
    public Decision approve(WorkTicket t, SysUser me, String comment) {
        upgradeLegacy(t, me);
        List<FlowNode> nodes = snapshot(t);
        int index = t.getCurrentNode();
        FlowNode node = nodes.get(index);
        ApprovalTask task = myPendingTask(t, me.getId());
        if (task == null) {
            throw new BizException(403, "你不是当前节点【" + node.name() + "】的审批人，或者已经审批过了");
        }
        decide(task, ApprovalRules.APPROVED, comment);
        String outcome = ApprovalRules.outcome(node.mode(), nodeStatuses(t, index));
        if (!ApprovalRules.APPROVED.equals(outcome)) {
            return Decision.WAITING;
        }
        // 节点通过：或签节点其他人的待办取消
        cancelPending(t.getId(), t.getApprovalRound(), index);
        if (index + 1 < nodes.size()) {
            t.setCurrentNode(index + 1);
            if (ticketMapper.updateById(t) == 0) {
                throw new BizException(409, "作业票已被他人处理，请刷新后重试");
            }
            activate(t, me);
            if (!Objects.equals(t.getApplicantId(), me.getId())) {
                messageService.send(List.of(t.getApplicantId()), MessageDraft.of(MessageCategory.NOTICE, TicketNotifier.BIZ_TYPE,
                        t.getId(), "审批进度：" + t.getTicketNo() + " " + t.getTitle(),
                        "【" + node.name() + "】已通过，进入【" + nodes.get(index + 1).name() + "】", TicketNotifier.link(t.getId())));
            }
            return Decision.NEXT;
        }
        t.setCurrentNode(null);
        return Decision.FINISHED;
    }

    /** 驳回：任何一个节点驳回，整张票退回申请人 */
    public void reject(WorkTicket t, SysUser me, String comment) {
        upgradeLegacy(t, me);
        ApprovalTask task = myPendingTask(t, me.getId());
        if (task == null) {
            FlowNode node = snapshot(t).get(t.getCurrentNode());
            throw new BizException(403, "你不是当前节点【" + node.name() + "】的审批人，或者已经审批过了");
        }
        decide(task, ApprovalRules.REJECTED, comment);
        cancelPending(t.getId(), t.getApprovalRound(), null);
        t.setCurrentNode(null);
    }

    /** 作业票取消：取消所有待办 */
    public void cancelAll(WorkTicket t) {
        if (t.getApprovalRound() != null && t.getApprovalRound() > 0) {
            cancelPending(t.getId(), t.getApprovalRound(), null);
        }
        t.setCurrentNode(null);
    }

    /** 轮到我审批了吗（前端据此显示同意 / 驳回按钮） */
    public boolean isMyTurn(WorkTicket t, Long me) {
        return isLegacy(t) || (t.getCurrentNode() != null && myPendingTask(t, me) != null);
    }

    /**
     * 升级前就已经提交、还在待审批的作业票：没有审批待办。
     * 第一次有人审批时，按默认流程（单节点、有审批权限的人或签）补上待办，不会卡住。
     */
    private boolean isLegacy(WorkTicket t) {
        return t.getStatus() == com.safework.ticket.enums.TicketStatus.PENDING
                && (t.getApprovalRound() == null || t.getApprovalRound() == 0);
    }

    private void upgradeLegacy(WorkTicket t, SysUser me) {
        if (!isLegacy(t)) {
            return;
        }
        t.setApprovalRound(1);
        t.setCurrentNode(0);
        t.setFlowSnapshot(write(ApprovalRules.DEFAULT_FLOW));
        if (ticketMapper.updateById(t) == 0) {
            throw new BizException(409, "作业票已被他人处理，请刷新后重试");
        }
        activate(t, me, false);
    }

    /** 当前节点名称，例如“企业负责人审批（2/2）” */
    public String currentStep(WorkTicket t) {
        if (t.getCurrentNode() == null || t.getFlowSnapshot() == null) {
            return null;
        }
        List<FlowNode> nodes = snapshot(t);
        return nodes.get(t.getCurrentNode()).name() + (nodes.size() > 1 ? "（" + (t.getCurrentNode() + 1) + "/" + nodes.size() + "）" : "");
    }

    public ApprovalProgressVO progress(WorkTicket t) {
        if (t.getApprovalRound() == null || t.getApprovalRound() == 0 || t.getFlowSnapshot() == null) {
            return null;
        }
        List<FlowNode> nodes = snapshot(t);
        Map<Integer, List<ApprovalTask>> byNode = taskMapper.selectList(Wrappers.<ApprovalTask>lambdaQuery()
                        .eq(ApprovalTask::getTicketId, t.getId())
                        .eq(ApprovalTask::getRoundNo, t.getApprovalRound())
                        .orderByAsc(ApprovalTask::getId))
                .stream().collect(Collectors.groupingBy(ApprovalTask::getNodeIndex));
        List<ApprovalProgressVO.Node> list = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            FlowNode n = nodes.get(i);
            List<ApprovalTask> tasks = byNode.getOrDefault(i, List.of());
            String status;
            if (tasks.isEmpty()) {
                status = "WAITING";
            } else {
                String outcome = ApprovalRules.outcome(n.mode(), tasks.stream().map(ApprovalTask::getStatus).toList());
                status = ApprovalRules.PENDING.equals(outcome)
                        ? (Objects.equals(t.getCurrentNode(), i) ? "ACTIVE" : ApprovalRules.CANCELLED)
                        : outcome;
            }
            list.add(new ApprovalProgressVO.Node(i, n.name(), n.mode(), describe(n), status, tasks.stream()
                    .map(k -> new ApprovalProgressVO.Task(k.getApproverName(), k.getStatus(), k.getComment(), k.getDecideTime()))
                    .toList()));
        }
        return new ApprovalProgressVO(t.getApprovalRound(), nodes.size(), t.getCurrentNode(), list);
    }

    /** 列表筛选“待我审批”用的子查询 */
    public static String myPendingSql(Long me) {
        return "SELECT ticket_id FROM ticket_approval_task WHERE status = 'PENDING' AND deleted = 0 AND approver_id = " + me;
    }

    // ============================== 内部方法 ==============================

    private ApprovalTask myPendingTask(WorkTicket t, Long me) {
        if (t.getCurrentNode() == null) {
            return null;
        }
        return taskMapper.selectOne(Wrappers.<ApprovalTask>lambdaQuery()
                .eq(ApprovalTask::getTicketId, t.getId())
                .eq(ApprovalTask::getRoundNo, t.getApprovalRound())
                .eq(ApprovalTask::getNodeIndex, t.getCurrentNode())
                .eq(ApprovalTask::getApproverId, me)
                .eq(ApprovalTask::getStatus, ApprovalRules.PENDING));
    }

    /** 用 WHERE status = 'PENDING' 条件更新：同一条待办只能处理一次 */
    private void decide(ApprovalTask task, String status, String comment) {
        ApprovalTask change = new ApprovalTask();
        change.setStatus(status);
        change.setComment(StringUtils.hasText(comment) ? comment.trim() : null);
        change.setDecideTime(LocalDateTime.now());
        int rows = taskMapper.update(change, Wrappers.<ApprovalTask>lambdaUpdate()
                .eq(ApprovalTask::getId, task.getId())
                .eq(ApprovalTask::getStatus, ApprovalRules.PENDING));
        if (rows == 0) {
            throw new BizException(409, "这条审批已经处理过了，请刷新");
        }
    }

    private List<String> nodeStatuses(WorkTicket t, int index) {
        return taskMapper.selectList(Wrappers.<ApprovalTask>lambdaQuery()
                        .select(ApprovalTask::getStatus)
                        .eq(ApprovalTask::getTicketId, t.getId())
                        .eq(ApprovalTask::getRoundNo, t.getApprovalRound())
                        .eq(ApprovalTask::getNodeIndex, index))
                .stream().map(ApprovalTask::getStatus).toList();
    }

    private void cancelPending(Long ticketId, Integer round, Integer nodeIndex) {
        ApprovalTask change = new ApprovalTask();
        change.setStatus(ApprovalRules.CANCELLED);
        taskMapper.update(change, Wrappers.<ApprovalTask>lambdaUpdate()
                .eq(ApprovalTask::getTicketId, ticketId)
                .eq(ApprovalTask::getRoundNo, round)
                .eq(nodeIndex != null, ApprovalTask::getNodeIndex, nodeIndex)
                .eq(ApprovalTask::getStatus, ApprovalRules.PENDING));
    }

    private String describe(FlowNode n) {
        return switch (n.approverType()) {
            case FlowNode.ROLE -> roleService.list().stream().filter(r -> r.getRoleCode().equals(n.roleCode()))
                    .map(SysRole::getRoleName).findFirst().orElse(n.roleCode());
            case FlowNode.USER -> users(n.userIds()).stream().map(SysUser::getNickname).collect(Collectors.joining("、"));
            default -> "有审批权限的人";
        };
    }

    private List<SysUser> users(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .filter(u -> Integer.valueOf(1).equals(u.getStatus()))
                .sorted(Comparator.comparing(SysUser::getId))
                .toList();
    }

    private List<FlowNode> snapshot(WorkTicket t) {
        return t.getFlowSnapshot() == null ? ApprovalRules.DEFAULT_FLOW : parse(t.getFlowSnapshot());
    }

    private List<FlowNode> parse(String s) {
        try {
            return json.readValue(s, new TypeReference<List<FlowNode>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审批流程数据损坏", e);
        }
    }

    private String write(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
