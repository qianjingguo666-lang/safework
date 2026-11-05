package com.safework.safety.job;

import com.safework.safety.entity.MeetingTeam;
import com.safework.safety.meeting.MeetingService;
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

/** 班前会提醒：每天 8:40 还没开班前会的班组，提醒班组长（一天一次） */
@Component
@RequiredArgsConstructor
public class MeetingRemindJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final MeetingService meetingService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "meeting-remind";
    }

    @Override
    public String name() {
        return "班前会提醒";
    }

    @Override
    public String cron() {
        return "0 40 8 * * *";
    }

    @Override
    public String description() {
        return "每天 8:40 检查班组：今天还没开班前会的，提醒班组长（开工前讲安全要点、点名、确认身体精神状态）";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            for (MeetingTeam team : meetingService.notHeldToday()) {
                if (messageService.sendOnce("meeting:" + team.getId() + ":" + today, Duration.ofDays(2), List.of(team.getLeaderId()),
                        MessageDraft.of(MessageCategory.TODO, MeetingService.BIZ_TYPE, null, "今天还没开班前会：" + team.getName(),
                                "开工前请召集班组成员开班前会：讲今天的作业和风险、点名、确认每个人的身体和精神状态", MeetingService.LINK))) {
                    sent.incrementAndGet();
                }
            }
        });
        return "提醒 " + sent + " 个班组" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
