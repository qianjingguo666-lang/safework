package com.safework.training.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.training.entity.TrainExam;
import com.safework.training.entity.TrainExamRecord;
import com.safework.training.enums.ExamStatus;
import com.safework.training.mapper.TrainExamMapper;
import com.safework.training.mapper.TrainExamRecordMapper;
import com.safework.training.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** 考试即将截止提醒：24 小时内结束的考试，提醒还没通过的人 */
@Component
@RequiredArgsConstructor
public class ExamReminderJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final TrainExamMapper examMapper;
    private final TrainExamRecordMapper recordMapper;
    private final ExamService examService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "exam-reminder";
    }

    @Override
    public String name() {
        return "考试截止提醒";
    }

    @Override
    public String cron() {
        return "0 10 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:10 检查 24 小时内结束的考试，提醒还没参加或没通过的人（每场考试只提醒一次）";
    }

    @Override
    public String execute() {
        AtomicInteger exams = new AtomicInteger();
        AtomicInteger users = new AtomicInteger();
        LocalDateTime now = LocalDateTime.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            for (TrainExam e : examMapper.selectList(Wrappers.<TrainExam>lambdaQuery()
                    .eq(TrainExam::getStatus, ExamStatus.PUBLISHED)
                    .gt(TrainExam::getEndTime, now)
                    .le(TrainExam::getEndTime, now.plusHours(24)))) {
                Set<Long> passed = recordMapper.selectList(Wrappers.<TrainExamRecord>lambdaQuery()
                                .select(TrainExamRecord::getUserId)
                                .eq(TrainExamRecord::getExamId, e.getId())
                                .eq(TrainExamRecord::getPassed, 1))
                        .stream().map(TrainExamRecord::getUserId).collect(Collectors.toSet());
                List<Long> todo = new ArrayList<>(examService.targetUserIds(e));
                todo.removeAll(passed);
                if (!todo.isEmpty() && messageService.sendOnce("exam-remind:" + e.getId(), Duration.ofDays(3), todo,
                        MessageDraft.of(MessageCategory.WARN, ExamService.BIZ_TYPE, e.getId(), "考试即将截止：" + e.getTitle(),
                                "考试将于 " + e.getEndTime().format(ExamService.FMT) + " 结束，你还没有参加或没有通过，请尽快完成",
                                ExamService.MY_LINK))) {
                    exams.incrementAndGet();
                    users.addAndGet(todo.size());
                }
            }
        });
        return "提醒 " + exams + " 场考试、" + users + " 人" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
