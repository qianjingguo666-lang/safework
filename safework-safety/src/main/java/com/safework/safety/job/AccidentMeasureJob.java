package com.safework.safety.job;

import com.safework.safety.accident.AccidentRules;
import com.safework.safety.accident.AccidentService;
import com.safework.safety.entity.AccidentInvestigation;
import com.safework.safety.entity.AccidentMeasure;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 事故整改提醒：
 * <ul>
 *   <li>整改责任人：期限前 3 天、1 天、当天，逾期后每 3 天</li>
 *   <li>调查超过期限还没结案：超期第 1 天、之后每 7 天提醒组长和管理员</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AccidentMeasureJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final AccidentService accidentService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "accident-measure";
    }

    @Override
    public String name() {
        return "事故整改提醒";
    }

    @Override
    public String cron() {
        return "0 20 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:20 检查事故整改措施和调查期限：提醒整改责任人措施到期、逾期；调查超期未结案时提醒组长和管理员";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            for (AccidentMeasure m : accidentService.openMeasures()) {
                if (!AccidentRules.shouldRemind(m.getDeadline(), today)) {
                    continue;
                }
                long d = ChronoUnit.DAYS.between(today, m.getDeadline());
                String title = d < 0 ? "事故整改措施已逾期 " + (-d) + " 天" : d == 0 ? "今天到期：事故整改措施" : d + " 天后到期：事故整改措施";
                if (messageService.sendOnce("accident-measure:" + m.getId() + ":" + today, Duration.ofDays(2), List.of(m.getOwnerId()),
                        MessageDraft.of(d < 0 ? MessageCategory.WARN : MessageCategory.TODO, AccidentService.BIZ_TYPE, m.getInvestigationId(),
                                title, m.getContent() + "（期限 " + m.getDeadline() + "）。完成后请填写落实情况", AccidentService.LINK))) {
                    sent.incrementAndGet();
                }
            }
            for (AccidentInvestigation inv : accidentService.openInvestigations()) {
                if (!AccidentRules.shouldWarnOverdue(inv.getDeadline(), today)) {
                    continue;
                }
                List<Long> to = new ArrayList<>(messageService.listPermissionUsers(AccidentService.PERM_EDIT));
                if (!to.contains(inv.getLeaderId())) {
                    to.add(inv.getLeaderId());
                }
                if (messageService.sendOnce("accident-overdue:" + inv.getId() + ":" + today, Duration.ofDays(2), to,
                        MessageDraft.of(MessageCategory.WARN, AccidentService.BIZ_TYPE, inv.getId(),
                                "事故调查超期未结案：" + inv.getEventTitle(),
                                "调查期限 " + inv.getDeadline() + " 已过，请尽快落实“四不放过”并结案", AccidentService.LINK))) {
                    sent.addAndGet(to.size());
                }
            }
        });
        return "提醒 " + sent + " 人次" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
