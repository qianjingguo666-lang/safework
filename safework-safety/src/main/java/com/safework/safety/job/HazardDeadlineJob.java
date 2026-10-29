package com.safework.safety.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardStatus;
import com.safework.safety.mapper.HazardMapper;
import com.safework.safety.service.HazardNotifier;
import com.safework.safety.service.HazardService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 隐患整改期限检查：
 * - 已逾期：每天提醒一次整改人和安全员（逾期一天提醒一天，直到整改）
 * - 24 小时内到期：提醒整改人一次
 */
@Component
@RequiredArgsConstructor
public class HazardDeadlineJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final HazardMapper hazardMapper;
    private final MessageService messageService;

    @Override
    public String code() {
        return "hazard-deadline";
    }

    @Override
    public String name() {
        return "隐患整改期限检查";
    }

    @Override
    public String cron() {
        return "0 30 8 * * *";
    }

    @Override
    public String description() {
        return "每天 8:30 检查整改中的隐患：已逾期的每天提醒整改人和安全员，24 小时内到期的提醒整改人";
    }

    @Override
    public String execute() {
        AtomicInteger overdue = new AtomicInteger();
        AtomicInteger soon = new AtomicInteger();
        LocalDateTime now = LocalDateTime.now();
        String today = LocalDate.now().toString();
        List<String> errors = tenantJobHelper.forEachActiveTenant(tenant -> {
            List<Hazard> rectifying = hazardMapper.selectList(Wrappers.<Hazard>lambdaQuery()
                    .eq(Hazard::getStatus, HazardStatus.RECTIFYING)
                    .isNotNull(Hazard::getDeadline)
                    .lt(Hazard::getDeadline, now.plusHours(24)));
            if (rectifying.isEmpty()) {
                return;
            }
            List<Long> handlers = messageService.listPermissionUsers(HazardService.PERM_HANDLE);
            for (Hazard h : rectifying) {
                String name = h.getHazardNo() + " " + h.getTitle();
                if (h.getDeadline().isBefore(now)) {
                    long days = Math.max(1, Duration.between(h.getDeadline(), now).toDays());
                    List<Long> receivers = new ArrayList<>(handlers);
                    receivers.add(h.getRectifierId());
                    if (messageService.sendOnce("hazard-overdue:" + h.getId() + ":" + today, Duration.ofDays(1), receivers,
                            MessageDraft.of(MessageCategory.WARN, HazardNotifier.BIZ_TYPE, h.getId(), "隐患逾期未整改：" + name,
                                    "整改期限 " + h.getDeadline().format(HazardNotifier.FMT) + "，已逾期 " + days + " 天，整改责任人："
                                            + h.getRectifierName(), HazardNotifier.link(h.getId())))) {
                        overdue.incrementAndGet();
                    }
                } else if (messageService.sendOnce("hazard-soon:" + h.getId(), Duration.ofDays(2), List.of(h.getRectifierId()),
                        MessageDraft.of(MessageCategory.TODO, HazardNotifier.BIZ_TYPE, h.getId(), "整改即将到期：" + name,
                                "整改期限 " + h.getDeadline().format(HazardNotifier.FMT) + "，请尽快完成整改并提交",
                                HazardNotifier.link(h.getId())))) {
                    soon.incrementAndGet();
                }
            }
        });
        return "检查 " + tenantJobHelper.activeCount() + " 家企业：新提醒逾期 " + overdue + " 条、即将到期 " + soon + " 条"
                + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
