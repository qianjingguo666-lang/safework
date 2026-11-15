package com.safework.ticket.handover;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.system.entity.SysUser;
import com.safework.system.message.*;
import com.safework.system.service.PermissionService;
import com.safework.system.service.UserService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.ShiftHandoverMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HandoverService {
    private final ShiftHandoverMapper records;
    private final WorkTicketMapper tickets;
    private final UserService users;
    private final PermissionService permissions;
    private final MessageService messages;

    public Page<ShiftHandover> page(long current, boolean awaitingMe, Long recordId) {
        Long me = StpUtil.getLoginIdAsLong();
        return records.selectPage(new Page<>(Math.max(1, current), 20),
                Wrappers.<ShiftHandover>lambdaQuery()
                    .eq(recordId != null, ShiftHandover::getId, recordId)
                    .eq(awaitingMe, ShiftHandover::getReceiverId, me)
                    .isNull(awaitingMe, ShiftHandover::getAcceptedAt)
                    .and(!StpUtil.hasPermission("ticket:approve"), w -> w.eq(ShiftHandover::getSenderId, me)
                            .or().eq(ShiftHandover::getReceiverId, me))
                    .orderByDesc(ShiftHandover::getCreateTime, ShiftHandover::getId));
    }

    @Transactional
    public Long create(HandoverController.Create form) {
        Long me = StpUtil.getLoginIdAsLong();
        WorkTicket ticket = tickets.selectOne(Wrappers.<WorkTicket>lambdaQuery()
                .eq(WorkTicket::getId, form.ticketId()).last("FOR UPDATE"));
        if (ticket == null) throw new BizException("作业票不存在");
        ShiftHandover last = records.selectOne(Wrappers.<ShiftHandover>lambdaQuery()
                .eq(ShiftHandover::getTicketId, ticket.getId())
                .orderByDesc(ShiftHandover::getCreateTime, ShiftHandover::getId).last("LIMIT 1"));
        if (!me.equals(ticket.getApplicantId()) && !me.equals(ticket.getGuardianId())
                && !(last != null && me.equals(last.getReceiverId()) && last.getAcceptedAt() != null)
                && !StpUtil.hasPermission("ticket:approve")) throw new BizException("无权交接此作业票");
        if (ticket.getStatus() != TicketStatus.IN_PROGRESS) throw new BizException("只有作业中的票可以交班");
        if (last != null && last.getAcceptedAt() == null) throw new BizException("上一次交班仍待接班人确认");
        SysUser receiver = users.getById(form.receiverId());
        if (receiver == null || !Integer.valueOf(1).equals(receiver.getStatus()) || me.equals(receiver.getId())
                || !permissions.getPermissions(receiver.getId()).contains("ticket:list"))
            throw new BizException("请选择另一位有作业票权限的在用员工");
        ShiftHandover record = new ShiftHandover();
        record.setTicketId(ticket.getId());
        record.setTicketNo(ticket.getTicketNo());
        record.setTitle(ticket.getTitle());
        record.setSenderId(me);
        record.setSenderName(users.getById(me).getNickname());
        record.setReceiverId(receiver.getId());
        record.setReceiverName(receiver.getNickname());
        record.setSiteCondition(form.siteCondition().strip());
        record.setUnfinished(form.unfinished().strip());
        record.setRecheck(form.recheck().strip());
        records.insert(record);
        messages.send(List.of(receiver.getId()), MessageDraft.of(MessageCategory.TODO, "SHIFT_HANDOVER",
                record.getId(), "有一份作业交班待确认", ticket.getTicketNo() + " " + ticket.getTitle(), "/ticket/handover"));
        return record.getId();
    }

    @Transactional
    public void accept(Long id, List<String> checked) {
        Long me = StpUtil.getLoginIdAsLong();
        ShiftHandover record = records.selectOne(Wrappers.<ShiftHandover>lambdaQuery()
                .eq(ShiftHandover::getId, id).eq(ShiftHandover::getReceiverId, me).last("FOR UPDATE"));
        if (record == null) throw new BizException("交接记录不存在");
        if (!HandoverRules.allChecked(checked)) throw new BizException("请逐项核对全部交班内容");
        if (record.getAcceptedAt() == null) {
            record.setAcceptedAt(LocalDateTime.now());
            records.updateById(record);
        }
    }
}
