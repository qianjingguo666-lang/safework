package com.safework.system.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.tenant.TenantContext;
import com.safework.system.entity.SysRole;
import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysUser;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.TenantService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 企业服务到期提醒：到期前 30、7、3、1 天和当天提醒企业管理员；同时给平台管理员发一份汇总 */
@Component
@RequiredArgsConstructor
public class TenantExpireJob implements ScheduledJob {

    private static final Set<Long> REMIND_DAYS = Set.of(30L, 7L, 3L, 1L, 0L);

    private final SysTenantMapper tenantMapper;
    private final UserService userService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "tenant-expire";
    }

    @Override
    public String name() {
        return "企业到期提醒";
    }

    @Override
    public String cron() {
        return "0 0 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:00 检查服务期限，到期前 30 / 7 / 3 / 1 天和当天提醒企业管理员续费，并给平台发汇总";
    }

    @Override
    public String execute() {
        LocalDateTime now = LocalDateTime.now();
        List<SysTenant> expiring = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                .ne(SysTenant::getId, TenantContext.PLATFORM_ID)
                .eq(SysTenant::getStatus, 1)
                .isNotNull(SysTenant::getExpireTime)
                .gt(SysTenant::getExpireTime, now)
                .le(SysTenant::getExpireTime, now.plusDays(31)));
        int reminded = 0;
        List<String> names = new ArrayList<>();
        for (SysTenant t : expiring) {
            long days = TenantService.daysLeft(t.getExpireTime());
            names.add(t.getTenantName() + "（" + days + " 天）");
            if (!REMIND_DAYS.contains(days)) {
                continue;
            }
            String title = days == 0 ? "企业服务今天到期" : "企业服务还有 " + days + " 天到期";
            boolean sent = TenantContext.callAs(t.getId(), () -> messageService.sendOnce(
                    "tenant-expire:" + days, Duration.ofDays(2), adminIds(),
                    MessageDraft.of(MessageCategory.WARN, "TENANT", t.getId(), title,
                            "服务到期时间 " + t.getExpireTime().toLocalDate() + "，到期后全部账号将无法登录，请及时联系平台续费。", null)));
            if (sent) {
                reminded++;
            }
        }
        if (!names.isEmpty()) {
            String summary = String.join("、", names.subList(0, Math.min(names.size(), 10)));
            TenantContext.runAs(TenantContext.PLATFORM_ID, () -> messageService.sendOnce(
                    "platform-expire:" + LocalDate.now(), Duration.ofDays(1), adminIds(),
                    MessageDraft.of(MessageCategory.WARN, "TENANT", null, names.size() + " 家企业 30 天内到期",
                            summary, "/platform/tenant?expiring=1")));
        }
        return "30 天内到期 " + expiring.size() + " 家，本次提醒 " + reminded + " 家";
    }

    /** 当前租户的管理员 */
    private List<Long> adminIds() {
        return userService.listByRoleCode(SysRole.ADMIN_CODE).stream().map(SysUser::getId).collect(Collectors.toList());
    }
}
