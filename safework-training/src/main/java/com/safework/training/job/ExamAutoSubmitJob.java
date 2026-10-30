package com.safework.training.job;

import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.training.service.ExamTakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 超时自动交卷：考生关了浏览器、断网，时间到了也要按已保存的答案出成绩 */
@Component
@RequiredArgsConstructor
public class ExamAutoSubmitJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final ExamTakeService takeService;

    @Override
    public String code() {
        return "exam-auto-submit";
    }

    @Override
    public String name() {
        return "考试超时自动交卷";
    }

    @Override
    public String cron() {
        return "0 */5 * * * *";
    }

    @Override
    public String description() {
        return "每 5 分钟检查一次：答题时间已到还没交卷的，按已保存的答案自动交卷并出成绩";
    }

    @Override
    public String execute() {
        AtomicInteger count = new AtomicInteger();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> count.addAndGet(takeService.autoSubmitExpired(null, false)));
        return "自动交卷 " + count + " 份" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
