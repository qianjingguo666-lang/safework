package com.safework.ticket.service;

import cn.dev33.satoken.stp.StpUtil;
import com.safework.system.file.BizAccessChecker;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 作业票附件（作业方案、气体检测记录、现场照片等）的权限：
 * - 查看：能看作业票详情的人
 * - 上传 / 删除：申请人和监护人，作业结束（已完成、已取消）后不能再改
 */
@Component
@RequiredArgsConstructor
public class TicketAccessChecker implements BizAccessChecker {

    public static final String TAG_ATTACH = "ATTACH";
    private static final Set<TicketStatus> CLOSED = Set.of(TicketStatus.COMPLETED, TicketStatus.CANCELLED);

    private final TicketService ticketService;

    @Override
    public String bizType() {
        return TicketNotifier.BIZ_TYPE;
    }

    @Override
    public void checkView(Long bizId) {
        ticketService.detail(bizId);
    }

    @Override
    public boolean canEdit(Long bizId, String tag) {
        if (!TAG_ATTACH.equals(tag)) {
            return false;
        }
        WorkTicket t = ticketService.detail(bizId).getTicket();
        Long me = StpUtil.getLoginIdAsLong();
        return !CLOSED.contains(t.getStatus()) && (me.equals(t.getApplicantId()) || me.equals(t.getGuardianId()));
    }
}
