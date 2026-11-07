package com.safework.ticket.job;

import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.ticket.entity.StdAssessment;
import com.safework.ticket.std.StdService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 标准化自评提醒：每年至少自评一次。每月 10 日检查，距上次完成超过 11 个月（或从没自评过）、又没有正在进行的自评，提醒管理员 */
@Component
@RequiredArgsConstructor
public class StdAnnualJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final StdService stdService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "std-annual";
    }

    @Override
    public String name() {
        return "标准化自评提醒";
    }

    @Override
    public String cron() {
        return "0 50 9 10 * *";
    }

    @Override
    public String description() {
        return "每月 10 日 9:50 检查：距上次完成标准化自评超过 11 个月（每年至少一次）且没有正在进行的自评，提醒有自评权限的人";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            if (stdService.currentDraft() != null) {
                return;
            }
            StdAssessment last = stdService.latestDone();
            if (last != null && last.getFinishDate().isAfter(today.minusMonths(11))) {
                return;
            }
            String content = last == null ? "还没有开展过安全生产标准化自评。按要求每年至少自评一次，系统会从各模块自动取证，给出建议扣分"
                    : "上次自评“" + last.getTitle() + "”是 " + last.getFinishDate() + " 完成的（" + ChronoUnit.MONTHS.between(last.getFinishDate(), today)
                    + " 个月前，得分 " + last.getScore() + "，" + last.getGrade() + "）。按要求每年至少自评一次，请安排今年的自评";
            if (messageService.sendOnce("std-annual:" + today.getYear() + "-" + today.getMonthValue(), Duration.ofDays(20),
                    messageService.listPermissionUsers(StdService.PERM_EDIT),
                    MessageDraft.of(MessageCategory.TODO, "STD", null, "请开展安全生产标准化自评", content, StdService.LINK))) {
                sent.incrementAndGet();
            }
        });
        return "发送提醒 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
