package com.safework.safety.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.check.CheckRules;
import com.safework.safety.check.CheckService;
import com.safework.safety.entity.CheckPlan;
import com.safework.safety.entity.CheckTask;
import com.safework.safety.mapper.CheckPlanMapper;
import com.safework.safety.mapper.CheckTaskMapper;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 安全检查任务：每天早上按计划生成今天的检查任务，并提醒逾期没检查的。
 * 逾期任务每天提醒检查人一次；第一次逾期时同时告诉安全员（之后不再重复打扰安全员）。
 */
@Component
@RequiredArgsConstructor
public class CheckTaskJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final CheckPlanMapper planMapper;
    private final CheckTaskMapper taskMapper;
    private final CheckService checkService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "check-task";
    }

    @Override
    public String name() {
        return "安全检查任务生成";
    }

    @Override
    public String cron() {
        return "0 0 7 * * *";
    }

    @Override
    public String description() {
        return "每天 7:00 按检查计划生成今天的检查任务（通知检查人）；逾期没检查的每天提醒检查人，第一次逾期时同时提醒安全员";
    }

    @Override
    public String execute() {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger reminded = new AtomicInteger();
        List<String> failed = new java.util.concurrent.CopyOnWriteArrayList<>();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<CheckPlan> plans = planMapper.selectList(Wrappers.<CheckPlan>lambdaQuery()
                    .eq(CheckPlan::getStatus, 1)
                    .le(CheckPlan::getNextDate, today));
            for (CheckPlan p : plans) {
                // 每个计划单独一个事务：一个计划出错不影响其他计划
                try {
                    if (checkService.generate(p, today) != null) {
                        created.incrementAndGet();
                    }
                } catch (RuntimeException e) {
                    failed.add(p.getName() + "：" + e.getMessage());
                }
            }
            List<CheckTask> overdue = taskMapper.selectList(Wrappers.<CheckTask>lambdaQuery()
                    .eq(CheckTask::getStatus, CheckRules.PENDING)
                    .lt(CheckTask::getDueDate, today)
                    .last("LIMIT 500"));
            if (overdue.isEmpty()) {
                return;
            }
            List<Long> safety = messageService.listPermissionUsers(CheckService.PERM_EDIT);
            for (CheckTask k : overdue) {
                long days = ChronoUnit.DAYS.between(k.getDueDate(), today);
                String title = "检查任务已逾期 " + days + " 天：" + k.getPlanName();
                String content = "检查区域：" + k.getLocation() + "，应于 " + k.getDueDate() + " 前完成，请尽快检查并提交";
                if (messageService.sendOnce("check-overdue:" + k.getId() + ":" + today, Duration.ofDays(2), List.of(k.getCheckerId()),
                        MessageDraft.of(MessageCategory.WARN, CheckService.BIZ_TYPE, k.getId(), title, content, CheckService.link(k.getId())))) {
                    reminded.incrementAndGet();
                }
                messageService.sendOnce("check-overdue-safety:" + k.getId(), Duration.ofDays(60), safety,
                        MessageDraft.of(MessageCategory.WARN, CheckService.BIZ_TYPE, k.getId(),
                                k.getCheckerName() + " 的检查任务逾期未完成：" + k.getPlanName(), content, CheckService.link(k.getId())));
            }
        });
        errors.addAll(failed);
        return "生成 " + created + " 张检查任务，提醒逾期 " + reminded + " 张" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
