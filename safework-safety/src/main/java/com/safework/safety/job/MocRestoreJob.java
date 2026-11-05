package com.safework.safety.job;

import com.safework.safety.entity.MocChange;
import com.safework.safety.moc.MocRules;
import com.safework.safety.moc.MocService;
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

/** 临时变更恢复提醒：到期前 3 天、当天提醒申请人；过期没恢复的，第 1 天起每 7 天提醒申请人和安全管理员 */
@Component
@RequiredArgsConstructor
public class MocRestoreJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final MocService mocService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "moc-restore";
    }

    @Override
    public String name() {
        return "临时变更恢复提醒";
    }

    @Override
    public String cron() {
        return "0 30 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:30 检查已投用的临时变更：到期前 3 天、当天提醒申请人恢复原状；过期没恢复的每 7 天提醒申请人和安全管理员";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            for (MocChange c : mocService.temporaryOpen()) {
                if (c.getRestoreDate() == null || !MocRules.shouldRemindRestore(c.getRestoreDate(), today)) {
                    continue;
                }
                long d = ChronoUnit.DAYS.between(today, c.getRestoreDate());
                List<Long> to = new ArrayList<>(List.of(c.getApplicantId()));
                if (d < 0) {
                    messageService.listPermissionUsers(MocService.PERM_EDIT).stream().filter(u -> !to.contains(u)).forEach(to::add);
                }
                String title = d < 0 ? "临时变更已超期 " + (-d) + " 天未恢复：" + c.getTitle() : d == 0 ? "今天到期：临时变更恢复原状" : d + " 天后到期：临时变更恢复原状";
                if (messageService.sendOnce("moc-restore:" + c.getId() + ":" + today, Duration.ofDays(2), to,
                        MessageDraft.of(d < 0 ? MessageCategory.WARN : MessageCategory.TODO, MocService.BIZ_TYPE, c.getId(), title,
                                c.getChangeNo() + "《" + c.getTitle() + "》恢复日期 " + c.getRestoreDate() + "。恢复原状后请在系统里登记；需要延长的请重新办理变更", MocService.LINK))) {
                    sent.addAndGet(to.size());
                }
            }
        });
        return "提醒 " + sent + " 人次" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
