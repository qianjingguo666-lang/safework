package com.safework.ticket.job;

import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.ticket.commit.CommitRules;
import com.safework.ticket.commit.CommitService;
import com.safework.ticket.commit.CommitVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** 安全风险研判承诺提醒：8:30、9:30 检查今天还差哪些方面没研判（提醒研判人员），研判完了还没承诺的提醒主要负责人 */
@Component
@RequiredArgsConstructor
public class CommitRemindJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final CommitService commitService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "commit-remind";
    }

    @Override
    public String name() {
        return "安全风险研判承诺提醒";
    }

    @Override
    public String cron() {
        return "0 30 8,9 * * *";
    }

    @Override
    public String description() {
        return "每天 8:30、9:30：还有方面没研判的提醒有研判权限的人（9:00 前完成）；研判完了还没承诺的提醒主要负责人（10:00 前公告）";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDateTime now = LocalDateTime.now();
        String slot = now.toLocalDate() + "-" + now.getHour();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            CommitVOs.Overview o = commitService.overview(now);
            if (o.committed()) {
                return;
            }
            if (!o.missing().isEmpty()) {
                String names = o.missing().stream().map(CommitRules.ASPECTS::get).collect(Collectors.joining("、"));
                if (messageService.sendOnce("commit-judge:" + slot, Duration.ofDays(1), messageService.listPermissionUsers(CommitService.PERM_JUDGE),
                        MessageDraft.of(MessageCategory.TODO, CommitService.BIZ_TYPE, null,
                                (o.judgeLate() ? "安全风险研判已超时：" : "今天的安全风险研判还差：") + names,
                                "请 " + CommitRules.JUDGE_DEADLINE + " 前完成研判，主要负责人 " + CommitRules.COMMIT_DEADLINE + " 前承诺公告", CommitService.LINK))) {
                    sent.incrementAndGet();
                }
            } else if (messageService.sendOnce("commit-sign:" + slot, Duration.ofDays(1), messageService.listPermissionUsers(CommitService.PERM_SIGN),
                    MessageDraft.of(MessageCategory.TODO, CommitService.BIZ_TYPE, null, "今天的安全风险研判已完成，请签字承诺",
                            "生产、设备、工艺、安全负责人都已研判" + (o.abnormal() != null && o.abnormal() > 0 ? "，发现 " + o.abnormal() + " 项异常（已写管控措施）" : "，没有异常")
                                    + "。请 " + CommitRules.COMMIT_DEADLINE + " 前手写签名承诺并公告", CommitService.LINK))) {
                sent.incrementAndGet();
            }
        });
        return "发送提醒 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
