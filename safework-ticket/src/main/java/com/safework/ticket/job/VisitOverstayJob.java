package com.safework.ticket.job;

import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.ticket.entity.VisitPass;
import com.safework.ticket.visit.VisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** 外来人员超时未离厂：每天 8:10 把过了日期还没入厂的预约标记为过期；昨天及以前入厂还没离厂的，提醒接待人和门岗核实 */
@Component
@RequiredArgsConstructor
public class VisitOverstayJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final VisitService visitService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "visit-overstay";
    }

    @Override
    public String name() {
        return "外来人员超时未离厂";
    }

    @Override
    public String cron() {
        return "0 10 8 * * *";
    }

    @Override
    public String description() {
        return "每天 8:10：过了预约日期还没入厂的预约标记为过期；以前入厂、到现在还没登记离厂的，提醒接待人和门岗核实（可能漏登记，也可能人还在厂里）";
    }

    @Override
    public String execute() {
        AtomicInteger expired = new AtomicInteger();
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            expired.addAndGet(visitService.expireOld(today));
            List<VisitPass> over = visitService.overstayed(today);
            if (over.isEmpty()) {
                return;
            }
            List<Long> to = new ArrayList<>(messageService.listPermissionUsers(VisitService.PERM_GATE));
            over.stream().map(VisitPass::getHostId).filter(id -> id != null && !to.contains(id)).distinct().forEach(to::add);
            String detail = over.stream().limit(10)
                    .map(p -> (p.getPlateNo() != null ? p.getPlateNo() + " " : "") + p.getVisitorName() + "（" + p.getCompany() + "，" + p.getPlanDate() + " 入厂，接待人 " + p.getHostName() + "）")
                    .collect(Collectors.joining("；"));
            if (messageService.sendOnce("visit-overstay:" + today, Duration.ofDays(2), to,
                    MessageDraft.of(MessageCategory.WARN, VisitService.BIZ_TYPE, null, "外来人员超时未离厂：" + over.size() + " 人（车）",
                            detail + "。请核实是否已离厂并补登记，发生事故时在厂清单要准确", VisitService.LINK))) {
                sent.incrementAndGet();
            }
        });
        return "标记过期 " + expired + " 条，发送提醒 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
