package com.safework.safety.job;

import com.safework.safety.cost.CostService;
import com.safework.safety.cost.CostVOs;
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

/** 安全费用月度提醒：每月 5 日检查今年的安全生产费用（没编计划、超预算、余额为负、使用偏慢），有问题给管理员发汇总 */
@Component
@RequiredArgsConstructor
public class CostMonthlyJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final CostService costService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "cost-monthly";
    }

    @Override
    public String name() {
        return "安全费用月度提醒";
    }

    @Override
    public String cron() {
        return "0 45 9 5 * *";
    }

    @Override
    public String description() {
        return "每月 5 日 9:45 检查今年的安全生产费用：没有编制计划、分类超预算、已用超过可用额度、使用进度偏慢的，给安全投入管理员发汇总";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            CostVOs.Report r = costService.report(today.getYear());
            if (r.alerts().isEmpty()) {
                return;
            }
            String title = r.hasPlan() ? "安全费用：" + r.alerts().size() + " 项需要关注" : "安全费用：今年还没有编制计划";
            String content = "已使用 " + r.used().toPlainString() + " 元" + (r.usageRate() == null ? "" : "（" + r.usageRate() + "%）")
                    + "。" + String.join("；", r.alerts());
            if (messageService.sendOnce("cost-monthly:" + today.getYear() + "-" + today.getMonthValue(), Duration.ofDays(20),
                    messageService.listPermissionUsers(CostService.PERM_EDIT),
                    MessageDraft.of(MessageCategory.WARN, CostService.BIZ_TYPE, null, title, content, CostService.LINK))) {
                sent.incrementAndGet();
            }
        });
        return "发送汇总 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
