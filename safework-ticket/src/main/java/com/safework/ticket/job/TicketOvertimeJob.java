package com.safework.ticket.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketStatus;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.ticket.service.TicketNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 作业票超时检查：作业中但已超过计划结束时间（人可能还在受限空间里）、已批准但过了计划时间没开工。
 * 同一张票只提醒一次。
 */
@Component
@RequiredArgsConstructor
public class TicketOvertimeJob implements ScheduledJob {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final TenantJobHelper tenantJobHelper;
    private final WorkTicketMapper ticketMapper;
    private final MessageService messageService;

    @Override
    public String code() {
        return "ticket-overtime";
    }

    @Override
    public String name() {
        return "作业票超时检查";
    }

    @Override
    public String cron() {
        return "0 */30 * * * *";
    }

    @Override
    public String description() {
        return "每 30 分钟检查：作业中但已超过计划结束时间的，提醒监护人和申请人；已批准但过期未开工的，提醒申请人";
    }

    @Override
    public String execute() {
        AtomicInteger overtime = new AtomicInteger();
        AtomicInteger expired = new AtomicInteger();
        LocalDateTime now = LocalDateTime.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(tenant -> {
            for (WorkTicket t : ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                    .eq(WorkTicket::getStatus, TicketStatus.IN_PROGRESS)
                    .lt(WorkTicket::getPlanEndTime, now))) {
                boolean sent = messageService.sendOnce("ticket-overtime:" + t.getId(), Duration.ofDays(7),
                        List.of(t.getGuardianId(), t.getApplicantId()),
                        MessageDraft.of(MessageCategory.WARN, TicketNotifier.BIZ_TYPE, t.getId(),
                                "作业超时：" + t.getTicketNo() + " " + t.getTitle(),
                                "计划结束时间 " + t.getPlanEndTime().format(FMT) + " 已过，作业仍未完工。请监护人确认现场人员安全，尽快完工或重新办票",
                                TicketNotifier.link(t.getId())));
                if (sent) {
                    overtime.incrementAndGet();
                }
            }
            for (WorkTicket t : ticketMapper.selectList(Wrappers.<WorkTicket>lambdaQuery()
                    .eq(WorkTicket::getStatus, TicketStatus.APPROVED)
                    .lt(WorkTicket::getPlanEndTime, now))) {
                boolean sent = messageService.sendOnce("ticket-expired:" + t.getId(), Duration.ofDays(30),
                        List.of(t.getApplicantId()),
                        MessageDraft.of(MessageCategory.NOTICE, TicketNotifier.BIZ_TYPE, t.getId(),
                                "作业票过期未开工：" + t.getTicketNo() + " " + t.getTitle(),
                                "计划时间已过仍未开工，这张作业票已不能使用。如仍需作业，请取消后重新申请",
                                TicketNotifier.link(t.getId())));
                if (sent) {
                    expired.incrementAndGet();
                }
            }
        });
        return "检查 " + tenantJobHelper.activeCount() + " 家企业：新提醒作业超时 " + overtime + " 张、过期未开工 " + expired + " 张"
                + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
