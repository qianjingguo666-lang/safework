package com.safework.safety.job;

import com.safework.safety.chem.ChemService;
import com.safework.safety.chem.ChemVOs;
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
import java.util.stream.Collectors;

/** 危化品超储提醒：每天检查一次，库存超过设计最大储量的，给管理员发一条汇总（没有超储不发） */
@Component
@RequiredArgsConstructor
public class ChemStockJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final ChemService chemService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "chem-stock";
    }

    @Override
    public String name() {
        return "危化品超储提醒";
    }

    @Override
    public String cron() {
        return "0 25 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:25 检查危化品库存：超过设计最大储量的（超储会改变重大危险源辨识结果），给管理员发汇总";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<ChemVOs.StockRow> over = chemService.overstocked();
            if (over.isEmpty()) {
                return;
            }
            String detail = over.stream().limit(10)
                    .map(r -> r.chemName() + " " + r.qty().stripTrailingZeros().toPlainString() + "/" + r.maxQty().stripTrailingZeros().toPlainString() + " 吨")
                    .collect(Collectors.joining("；"));
            if (messageService.sendOnce("chem-overstock:" + today, Duration.ofDays(2), messageService.listPermissionUsers(ChemService.PERM_EDIT),
                    MessageDraft.of(MessageCategory.WARN, ChemService.BIZ_TYPE, null, "危化品超储：" + over.size() + " 项超过设计最大储量",
                            detail + "。超储会改变重大危险源辨识结果，请尽快出库或调整", ChemService.LINK))) {
                sent.incrementAndGet();
            }
        });
        return "发送汇总 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
