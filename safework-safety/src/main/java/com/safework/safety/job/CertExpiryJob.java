package com.safework.safety.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.cert.CertRules;
import com.safework.safety.cert.CertService;
import com.safework.safety.entity.PersonCert;
import com.safework.safety.mapper.PersonCertMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 证书到期提醒：剩 30 天、7 天、到期当天各提醒一次持证人和安全员。
 * 不天天提醒——提醒太多大家就不看了；已过期的在页面和开票时会被拦住。
 */
@Component
@RequiredArgsConstructor
public class CertExpiryJob implements ScheduledJob {

    static final String LINK = "/safety/cert";

    private final TenantJobHelper tenantJobHelper;
    private final PersonCertMapper certMapper;
    private final MessageService messageService;

    @Override
    public String code() {
        return "cert-expiry";
    }

    @Override
    public String name() {
        return "证书到期提醒";
    }

    @Override
    public String cron() {
        return "0 50 8 * * *";
    }

    @Override
    public String description() {
        return "每天 8:50 检查人员资质证书：有效期或复审日期剩 30 天、7 天和到期当天，各提醒一次持证人和安全员";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<LocalDate> days = CertRules.REMIND_DAYS.stream().map(today::plusDays).toList();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<PersonCert> certs = certMapper.selectList(Wrappers.<PersonCert>lambdaQuery().in(PersonCert::getValidUntil, days));
            if (certs.isEmpty()) {
                return;
            }
            List<Long> safety = messageService.listPermissionUsers(CertService.PERM_EDIT);
            for (PersonCert c : certs) {
                if (!CertRules.shouldRemind(c.getValidUntil(), today)) {
                    continue;
                }
                long left = ChronoUnit.DAYS.between(today, c.getValidUntil());
                String label = CertRules.CERT_TYPES.getOrDefault(c.getCertType(), c.getCertType());
                String what = CertRules.limitName(c.getExpireDate(), c.getReviewDate());
                String when = left == 0 ? "今天到期" : left + " 天后到期（" + c.getValidUntil() + "）";
                Set<Long> receivers = new LinkedHashSet<>(safety);
                receivers.add(c.getUserId());
                if (messageService.sendOnce("cert:" + c.getId() + ":" + c.getValidUntil() + ":" + left, Duration.ofDays(2), receivers,
                        MessageDraft.of(left <= 7 ? MessageCategory.WARN : MessageCategory.TODO, CertService.BIZ_TYPE, c.getId(),
                                c.getUserName() + " 的" + label + what + when,
                                "证书编号 " + c.getCertNo() + "。" + ("复审".equals(what) ? "请及时参加复审" : "请及时换证")
                                        + "，过期后不能再担任需要持证的作业负责人或监护人", LINK))) {
                    sent.incrementAndGet();
                }
            }
        });
        return "提醒 " + sent + " 张证书" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
