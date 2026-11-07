package com.safework.safety.job;

import com.safework.safety.injury.InjuryRules;
import com.safework.safety.injury.InjuryService;
import com.safework.safety.injury.InjuryVOs;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 工伤认定申请期限提醒：受伤 30 日内要申请认定，剩 7 / 3 / 1 / 0 天和超期后每天提醒工伤管理员 */
@Component
@RequiredArgsConstructor
public class InjuryDeadlineJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final InjuryService injuryService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "injury-deadline";
    }

    @Override
    public String name() {
        return "工伤认定申请期限提醒";
    }

    @Override
    public String cron() {
        return "0 55 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:55：受伤后还没申请工伤认定的，剩 7 / 3 / 1 / 0 天和超期后提醒工伤管理员（《工伤保险条例》第十七条：30 日内申请，逾期期间的费用由单位负担）";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<Long> to = messageService.listPermissionUsers(InjuryService.PERM_EDIT);
            for (InjuryVOs.Row r : injuryService.pendingApply(today)) {
                InjuryRules.Deadline d = r.deadline();
                if (!InjuryRules.shouldRemind(d)) {
                    continue;
                }
                boolean over = InjuryRules.OVERDUE.equals(d.state());
                String who = r.injury().getInjuredName() + "（" + r.injury().getEmployerName() + "）";
                String title = over ? "工伤认定申请已超期 " + (-d.daysLeft()) + " 天：" + who : "工伤认定申请还剩 " + d.daysLeft() + " 天：" + who;
                String content = r.injury().getInjuryTime().toLocalDate() + " 在" + r.injury().getLocation() + "受伤，应在 " + d.dueDate() + " 前申请工伤认定"
                        + (over ? "。已超期，超期这段时间的工伤待遇等费用由单位负担，请尽快申请" : "")
                        + (InjuryRules.CONTRACTOR.equals(r.injury().getEmployerType()) ? "（承包商人员，请督促其单位申请）" : "");
                if (messageService.sendOnce("injury-deadline:" + r.injury().getId() + ":" + today, Duration.ofDays(2), to,
                        MessageDraft.of(over ? MessageCategory.WARN : MessageCategory.TODO, InjuryService.BIZ_TYPE, r.injury().getId(), title, content, InjuryService.LINK))) {
                    sent.incrementAndGet();
                }
            }
        });
        return "发送提醒 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
