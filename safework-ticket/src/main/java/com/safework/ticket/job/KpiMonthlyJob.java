package com.safework.ticket.job;

import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.ticket.kpi.KpiRules;
import com.safework.ticket.kpi.KpiService;
import com.safework.ticket.kpi.KpiVOs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** 安全目标月报：每月 1 日给安全管理员发上个月为止的目标完成情况，列出红灯、黄灯指标 */
@Component
@RequiredArgsConstructor
public class KpiMonthlyJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final KpiService kpiService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "kpi-monthly";
    }

    @Override
    public String name() {
        return "安全目标月报";
    }

    @Override
    public String cron() {
        return "0 35 9 1 * *";
    }

    @Override
    public String description() {
        return "每月 1 日 9:35 统计年度安全目标完成情况（年初至上月底），给安全管理员发月报，列出红灯、黄灯指标";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            KpiVOs.Report r = kpiService.report(lastMonth.getYear());
            String bad = r.items().stream().filter(i -> KpiRules.RED.equals(i.light()) || KpiRules.YELLOW.equals(i.light()))
                    .map(i -> (KpiRules.RED.equals(i.light()) ? "【红】" : "【黄】") + i.label() + " " + fmt(i.actual()) + i.unit()
                            + "（目标 " + fmt(i.target()) + i.unit() + "）")
                    .collect(Collectors.joining("；"));
            String title = lastMonth.getYear() + " 年安全目标月报：" + (r.totalScore() == null ? "暂无数据" : r.totalScore() + " 分")
                    + "，绿灯 " + r.green() + " 项、黄灯 " + r.yellow() + " 项、红灯 " + r.red() + " 项";
            if (messageService.sendOnce("kpi-monthly:" + lastMonth.getYear() + "-" + lastMonth.getMonthValue(), Duration.ofDays(40),
                    messageService.listPermissionUsers(KpiService.PERM_EDIT),
                    MessageDraft.of(r.red() > 0 ? MessageCategory.WARN : MessageCategory.NOTICE, "KPI", null, title,
                            bad.isEmpty() ? "所有指标都达到目标" : bad, KpiService.LINK))) {
                sent.incrementAndGet();
            }
        });
        return "发送月报 " + sent + " 份" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }

    private static String fmt(Double v) {
        if (v == null) {
            return "-";
        }
        return v == Math.floor(v) ? String.valueOf(v.longValue()) : String.valueOf(v);
    }
}
