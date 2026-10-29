package com.safework.ticket.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.core.R;
import com.safework.common.tenant.TenantContext;
import com.safework.safety.entity.ConfinedSpace;
import com.safework.safety.service.SpaceService;
import com.safework.system.entity.SysTenant;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.service.DeptService;
import com.safework.ticket.dto.ScanVO;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 扫码查看受限空间（不需要登录，已加入白名单 /public/**）。
 * 现场张贴二维码，任何人扫码都能看到：这个空间有哪些危险介质、现在有没有人在作业、气体检测是否合格。
 *
 * 难点：没有登录就没有租户上下文。做法是先用 qr_token 跳过租户插件查到空间，
 * 再把空间所属的租户 ID 临时放进 TenantContext，后续查询照常被隔离在这个企业内。
 */
@RestController
@RequestMapping("/public/space")
@RequiredArgsConstructor
public class ScanController {

    private final SpaceService spaceService;
    private final WorkTicketMapper ticketMapper;
    private final SysTenantMapper tenantMapper;
    private final DeptService deptService;

    @GetMapping("/{token}")
    public R<ScanVO> scan(@PathVariable String token) {
        ConfinedSpace space = spaceService.getByQrToken(token);
        TenantContext.set(space.getTenantId());
        try {
            ScanVO vo = new ScanVO();
            SysTenant tenant = tenantMapper.selectById(space.getTenantId());
            vo.setTenantName(tenant == null ? null : tenant.getTenantName());
            vo.setSpaceCode(space.getSpaceCode());
            vo.setSpaceName(space.getSpaceName());
            vo.setSpaceType(space.getSpaceType());
            vo.setLocation(space.getLocation());
            vo.setDeptName(deptService.nameOf(space.getDeptId()));
            vo.setResponsibleName(space.getResponsibleName());
            vo.setMedium(space.getMedium());
            vo.setRiskLevel(space.getRiskLevel());

            List<WorkTicket> active = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                    .eq(WorkTicket::getSpaceId, space.getId())
                    .in(WorkTicket::getStatus, TicketStatus.APPROVED, TicketStatus.IN_PROGRESS)
                    .orderByAsc(WorkTicket::getPlanStartTime));
            List<WorkTicket> recent = ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                    .eq(WorkTicket::getSpaceId, space.getId())
                    .eq(WorkTicket::getStatus, TicketStatus.COMPLETED)
                    .orderByDesc(WorkTicket::getActualEndTime)
                    .last("LIMIT 3"));
            vo.setActiveTickets(active.stream().map(ScanController::brief).toList());
            vo.setRecentTickets(recent.stream().map(ScanController::brief).toList());
            vo.setWorking(active.stream().anyMatch(t -> t.getStatus() == TicketStatus.IN_PROGRESS));
            return R.ok(vo);
        } finally {
            TenantContext.clear();
        }
    }

    private static ScanVO.TicketBrief brief(WorkTicket t) {
        ScanVO.TicketBrief b = new ScanVO.TicketBrief();
        b.setTicketNo(t.getTicketNo());
        b.setTitle(t.getTitle());
        b.setStatus(t.getStatus().name());
        b.setApplicantName(t.getApplicantName());
        b.setGuardianName(t.getGuardianName());
        b.setPlanStartTime(t.getPlanStartTime());
        b.setPlanEndTime(t.getPlanEndTime());
        b.setActualStartTime(t.getActualStartTime());
        b.setActualEndTime(t.getActualEndTime());
        b.setGasOxygen(t.getGasOxygen());
        b.setGasLel(t.getGasLel());
        b.setGasH2s(t.getGasH2s());
        b.setGasCheckTime(t.getGasCheckTime());
        return b;
    }
}
