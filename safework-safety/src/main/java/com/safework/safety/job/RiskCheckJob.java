package com.safework.safety.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.entity.RiskPoint;
import com.safework.safety.enums.RiskLevel;
import com.safework.safety.mapper.RiskPointMapper;
import com.safework.safety.service.RiskService;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 风险点巡检提醒：
 * - 今天到期：提醒责任人
 * - 已逾期：每天提醒责任人；重大、较大风险点逾期同时提醒安全员（升级）
 */
@Component
@RequiredArgsConstructor
public class RiskCheckJob implements ScheduledJob {

    static final String BIZ_TYPE = "RISK";
    static final String LINK = "/safety/risk-check";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final TenantJobHelper tenantJobHelper;
    private final RiskPointMapper pointMapper;
    private final MessageService messageService;

    @Override
    public String code() {
        return "risk-check";
    }

    @Override
    public String name() {
        return "风险点巡检提醒";
    }

    @Override
    public String cron() {
        return "0 40 8 * * *";
    }

    @Override
    public String description() {
        return "每天 8:40 检查风险点巡检：今天到期的提醒责任人；逾期的每天提醒，重大、较大风险点逾期同时提醒安全员";
    }

    @Override
    public String execute() {
        AtomicInteger due = new AtomicInteger();
        AtomicInteger overdue = new AtomicInteger();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<RiskPoint> points = pointMapper.selectList(Wrappers.<RiskPoint>lambdaQuery()
                    .eq(RiskPoint::getStatus, 1)
                    .lt(RiskPoint::getNextCheckTime, today.plusDays(1).atStartOfDay()));
            if (points.isEmpty()) {
                return;
            }
            List<Long> safety = messageService.listPermissionUsers(RiskService.PERM_EDIT);
            for (RiskPoint p : points) {
                String name = p.getPointCode() + " " + p.getPointName() + "（" + p.getRiskLevel().getLabel() + "）";
                if (p.getNextCheckTime().isBefore(now)) {
                    List<Long> receivers = new ArrayList<>(List.of(p.getResponsibleId()));
                    if (p.getRiskLevel() == RiskLevel.MAJOR || p.getRiskLevel() == RiskLevel.HIGH) {
                        receivers.addAll(safety);
                    }
                    if (messageService.sendOnce("risk-overdue:" + p.getId() + ":" + today, Duration.ofDays(1), receivers,
                            MessageDraft.of(MessageCategory.WARN, BIZ_TYPE, p.getId(), "风险点巡检逾期：" + name,
                                    "应于 " + p.getNextCheckTime().format(FMT) + " 前巡检，责任人：" + p.getResponsibleName(), LINK))) {
                        overdue.incrementAndGet();
                    }
                } else if (messageService.sendOnce("risk-due:" + p.getId() + ":" + today, Duration.ofDays(1), List.of(p.getResponsibleId()),
                        MessageDraft.of(MessageCategory.TODO, BIZ_TYPE, p.getId(), "今天需要巡检：" + name,
                                "请在 " + p.getNextCheckTime().format(FMT) + " 前逐条确认管控措施", LINK))) {
                    due.incrementAndGet();
                }
            }
        });
        return "提醒今天到期 " + due + " 个、逾期 " + overdue + " 个风险点" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
