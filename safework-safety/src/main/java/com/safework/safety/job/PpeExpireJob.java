package com.safework.safety.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.entity.PpeItem;
import com.safework.safety.mapper.PpeItemMapper;
import com.safework.safety.ppe.PpeRules;
import com.safework.safety.ppe.PpeService;
import com.safework.safety.ppe.PpeVOs;
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

/**
 * 劳保用品到期提醒：本人在到期前 7 天、到期当天、过期后每 7 天收到提醒；
 * 管理员每天一条汇总（多少人需要补发、哪些用品库存不足），没有问题就不发。
 */
@Component
@RequiredArgsConstructor
public class PpeExpireJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final PpeService ppeService;
    private final PpeItemMapper itemMapper;
    private final MessageService messageService;

    @Override
    public String code() {
        return "ppe-expire";
    }

    @Override
    public String name() {
        return "劳保用品到期提醒";
    }

    @Override
    public String cron() {
        return "0 5 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:05 检查劳保用品：到期前 7 天、到期当天、过期后每 7 天提醒本人；给管理员发一条汇总（需要补发的人数、库存不足的用品）";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<PpeVOs.LedgerRow> rows = ppeService.compute(null, null, today);
            for (PpeVOs.LedgerRow r : rows) {
                if (!PpeRules.shouldRemind(r.expireDate(), today)) {
                    continue;
                }
                long d = r.daysLeft();
                String title = d < 0 ? "劳保用品已过期 " + (-d) + " 天：" + r.itemName()
                        : d == 0 ? "劳保用品今天到期：" + r.itemName() : "劳保用品 7 天后到期：" + r.itemName();
                if (messageService.sendOnce("ppe:" + r.userId() + ":" + r.itemId() + ":" + today, Duration.ofDays(2), List.of(r.userId()),
                        MessageDraft.of(d < 0 ? MessageCategory.WARN : MessageCategory.NOTICE, "PPE", null, title,
                                "发放于 " + r.issueDate() + "，" + r.expireDate() + " 到期。过期的劳保用品防护效果不能保证，请找安全员更换", "/safety/ppe"))) {
                    sent.incrementAndGet();
                }
            }
            long need = rows.stream().filter(r -> PpeRules.needIssue(r.state())).map(PpeVOs.LedgerRow::userId).distinct().count();
            List<PpeItem> low = itemMapper.selectList(Wrappers.<PpeItem>lambdaQuery().eq(PpeItem::getStatus, 1).apply("stock < min_stock"));
            if (need == 0 && low.isEmpty()) {
                return;
            }
            String content = (need > 0 ? need + " 人有未发放或已过期的劳保用品，可以在台账里一键补发。" : "")
                    + (low.isEmpty() ? "" : "库存不足：" + String.join("、", low.stream().map(i -> i.getName() + " 剩 " + i.getStock() + i.getUnit()).toList()));
            messageService.sendOnce("ppe-summary:" + today, Duration.ofDays(2), messageService.listPermissionUsers(PpeService.PERM_EDIT),
                    MessageDraft.of(MessageCategory.WARN, "PPE", null, "劳保用品：" + (need > 0 ? need + " 人需要补发" : "库存不足"), content, "/safety/ppe"));
        });
        return "提醒 " + sent + " 人次" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
