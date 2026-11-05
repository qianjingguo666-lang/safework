package com.safework.safety.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.entity.OhExam;
import com.safework.safety.health.HealthRules;
import com.safework.safety.health.HealthService;
import com.safework.safety.health.HealthVOs;
import com.safework.safety.mapper.OhExamMapper;
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
 * 职业健康体检提醒：
 * <ul>
 *   <li>本人：下次体检前 30 天、7 天、当天，逾期后每 7 天；复查日期前 3 天、当天</li>
 *   <li>管理员：每天一条汇总（从没体检、逾期、禁忌证仍在接害岗位、异常未处理），没有问题不发</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class HealthExamJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final HealthService healthService;
    private final OhExamMapper examMapper;
    private final MessageService messageService;

    @Override
    public String code() {
        return "health-exam";
    }

    @Override
    public String name() {
        return "职业健康体检提醒";
    }

    @Override
    public String cron() {
        return "0 10 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:10 检查职业健康体检：提醒本人体检到期、逾期和复查日期；给管理员发汇总（从没体检、逾期、禁忌证仍在岗、异常未处理）";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<HealthVOs.LedgerRow> rows = healthService.compute(null, null, today);
            for (HealthVOs.LedgerRow r : rows) {
                if (!HealthRules.shouldRemind(r.nextExamDate(), today)) {
                    continue;
                }
                long d = r.daysLeft();
                String title = d < 0 ? "职业健康体检已逾期 " + (-d) + " 天" : d == 0 ? "今天到期：职业健康体检" : d + " 天后到期：职业健康体检";
                if (messageService.sendOnce("health:" + r.userId() + ":" + today, Duration.ofDays(2), List.of(r.userId()),
                        MessageDraft.of(d < 0 ? MessageCategory.WARN : MessageCategory.NOTICE, "HEALTH", null, title,
                                "你接触 " + r.factors() + "，每 " + r.cycleMonths() + " 个月要做一次在岗体检，下次应在 " + r.nextExamDate()
                                        + " 前完成。请联系职业卫生管理员安排", HealthService.LINK))) {
                    sent.incrementAndGet();
                }
            }
            // 复查提醒：复查日期前 3 天、当天
            examMapper.selectList(Wrappers.<OhExam>lambdaQuery()
                    .eq(OhExam::getHandleStatus, HealthRules.OPEN)
                    .in(OhExam::getRecheckDate, today, today.plusDays(3))).forEach(e -> {
                if (messageService.sendOnce("health-recheck:" + e.getId() + ":" + today, Duration.ofDays(2), List.of(e.getUserId()),
                        MessageDraft.of(MessageCategory.WARN, "HEALTH", null, "职业健康复查：请在 " + e.getRecheckDate() + " 去复查",
                                e.getExamDate() + " 的体检结论是需要复查" + (e.getDetail() == null ? "" : "（" + e.getDetail() + "）"), HealthService.LINK))) {
                    sent.incrementAndGet();
                }
            });
            long never = rows.stream().filter(r -> HealthRules.NEVER.equals(r.state())).count();
            long overdue = rows.stream().filter(r -> HealthRules.OVERDUE.equals(r.state())).count();
            long contra = rows.stream().filter(HealthVOs.LedgerRow::contraindication).count();
            long open = examMapper.selectCount(Wrappers.<OhExam>lambdaQuery().eq(OhExam::getHandleStatus, HealthRules.OPEN));
            if (never + overdue + contra + open == 0) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (contra > 0) {
                sb.append(contra).append(" 名职业禁忌证人员仍在接害岗位，应尽快调岗；");
            }
            if (overdue > 0) {
                sb.append(overdue).append(" 人体检逾期；");
            }
            if (never > 0) {
                sb.append(never).append(" 人没有上岗前或在岗体检；");
            }
            if (open > 0) {
                sb.append(open).append(" 条异常体检还没处理；");
            }
            messageService.sendOnce("health-summary:" + today, Duration.ofDays(2), messageService.listPermissionUsers(HealthService.PERM_EDIT),
                    MessageDraft.of(MessageCategory.WARN, "HEALTH", null, "职业健康：有 " + (never + overdue + contra + open) + " 项需要处理",
                            sb.toString(), HealthService.LINK));
        });
        return "提醒 " + sent + " 人次" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
