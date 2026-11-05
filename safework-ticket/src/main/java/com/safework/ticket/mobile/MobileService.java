package com.safework.ticket.mobile;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.check.CheckService;
import com.safework.safety.entity.Hazard;
import com.safework.safety.health.HealthRules;
import com.safework.safety.health.HealthService;
import com.safework.safety.health.HealthVOs;
import com.safework.safety.ppe.PpeRules;
import com.safework.safety.ppe.PpeService;
import com.safework.safety.ppe.PpeVOs;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.safety.service.HazardService;
import com.safework.safety.service.RiskService;
import com.safework.system.message.MessageService;
import com.safework.ticket.approval.ApprovalService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.ticket.resp.RespService;
import com.safework.safety.accident.AccidentService;
import com.safework.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 手机端首页：一次请求把“我现在要处理的事”数出来，现场人员打开手机就知道先做什么。
 * <p>
 * 口径和电脑端各列表一致：待我审批 = 当前节点有我的审批任务；我的作业 = 我是申请人或监护人；
 * 待我整改 = 整改责任人是我。全部是计数查询，带租户隔离。
 */
@Service
@RequiredArgsConstructor
public class MobileService {

    private static final List<TicketStatus> WORKING = List.of(TicketStatus.APPROVED, TicketStatus.IN_PROGRESS);

    private final WorkTicketMapper ticketMapper;
    private final HazardMapper hazardMapper;
    private final RiskPointMapper riskPointMapper;
    private final MessageService messageService;
    private final CheckService checkService;
    private final PpeService ppeService;
    private final HealthService healthService;
    private final RespService respService;
    private final AccidentService accidentService;

    public MobileHomeVO home() {
        Long me = StpUtil.getLoginIdAsLong();
        LocalDateTime now = LocalDateTime.now();

        Long approve = null;
        if (StpUtil.hasPermission(TicketService.PERM_APPROVE)) {
            approve = ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery()
                    .eq(WorkTicket::getStatus, TicketStatus.PENDING)
                    .inSql(WorkTicket::getId, ApprovalService.myPendingSql(me)));
        }
        Long working = null;
        if (StpUtil.hasPermission("ticket:list")) {
            working = ticketMapper.selectCount(Wrappers.<WorkTicket>lambdaQuery()
                    .in(WorkTicket::getStatus, WORKING)
                    .and(w -> w.eq(WorkTicket::getApplicantId, me).or().eq(WorkTicket::getGuardianId, me)));
        }

        // 整改责任人不一定有隐患菜单，所以“待我整改”不看权限
        long rectify = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getStatus, HazardStatus.RECTIFYING)
                .eq(Hazard::getRectifierId, me));
        long rectifyOverdue = rectify == 0 ? 0 : hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery()
                .eq(Hazard::getStatus, HazardStatus.RECTIFYING)
                .eq(Hazard::getRectifierId, me)
                .lt(Hazard::getDeadline, now));

        Long assign = null;
        Long verify = null;
        if (StpUtil.hasPermission(HazardService.PERM_HANDLE)) {
            assign = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().eq(Hazard::getStatus, HazardStatus.REPORTED));
            verify = hazardMapper.selectCount(Wrappers.<Hazard>lambdaQuery().eq(Hazard::getStatus, HazardStatus.PENDING_VERIFY));
        }

        Long riskDue = null;
        if (StpUtil.hasPermission("risk:check")) {
            // 和巡检任务页口径一致：安全员看全部，其他人看自己负责的；今天之内到期的都算
            boolean all = StpUtil.hasPermission(RiskService.PERM_EDIT);
            riskDue = riskPointMapper.selectCount(Wrappers.<RiskPoint>lambdaQuery()
                    .eq(RiskPoint::getStatus, 1)
                    .eq(!all, RiskPoint::getResponsibleId, me)
                    .lt(RiskPoint::getNextCheckTime, LocalDate.now().plusDays(1).atStartOfDay()));
        }

        // 安全检查：检查人不一定有菜单（临时安排的人），有分给自己的任务也显示
        int[] check = checkService.myPending();
        boolean showCheck = StpUtil.hasPermission(CheckService.PERM_LIST) || check[0] > 0;

        // 我的劳保：需要处理的（未发放、已过期、快到期）
        List<PpeVOs.LedgerRow> ppe = ppeService.mine().stream().filter(r -> !PpeRules.OK.equals(r.state())).toList();
        boolean showPpe = StpUtil.hasPermission(PpeService.PERM_LIST) || !ppe.isEmpty();

        // 职业健康：只有在接害岗位上的人才显示
        HealthVOs.LedgerRow health = healthService.mine().ledger();

        return new MobileHomeVO(num(approve), num(working), (int) rectify, (int) rectifyOverdue, num(assign), num(verify), num(riskDue),
                showCheck ? check[0] : null, showCheck ? check[1] : null,
                showPpe ? ppe.size() : null, showPpe ? (int) ppe.stream().filter(r -> PpeRules.needIssue(r.state())).count() : null,
                health == null ? null : (HealthRules.OK.equals(health.state()) ? 0 : 1) + health.openAbnormal(),
                health == null ? null : health.state(),
                respService.myPending(), accidentService.myPending(),
                (int) messageService.unreadCount(me));
    }

    private static Integer num(Long v) {
        return v == null ? null : v.intValue();
    }
}
