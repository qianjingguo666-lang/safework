package com.safework.ticket.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.ticket.entity.RespBook;
import com.safework.ticket.entity.RespSign;
import com.safework.ticket.mapper.RespBookMapper;
import com.safework.ticket.resp.RespRules;
import com.safework.ticket.resp.RespService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 责任书签署提醒：签署中的责任书，还没签的人在截止前 3 天、1 天、当天各提醒一次，逾期后每 3 天提醒一次；
 * 第一次逾期时告诉管理员还有多少人没签。
 */
@Component
@RequiredArgsConstructor
public class RespSignJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final RespBookMapper bookMapper;
    private final RespService respService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "resp-sign";
    }

    @Override
    public String name() {
        return "责任书签署提醒";
    }

    @Override
    public String cron() {
        return "0 15 9 * * *";
    }

    @Override
    public String description() {
        return "每天 9:15 检查签署中的安全生产责任书：没签的人截止前 3 天、1 天、当天各提醒一次，逾期后每 3 天提醒；第一次逾期时告诉管理员";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            for (RespBook b : bookMapper.selectList(Wrappers.<RespBook>lambdaQuery().eq(RespBook::getStatus, RespRules.PUBLISHED))) {
                List<RespSign> pending = respService.pendingOf(b.getId());
                if (pending.isEmpty() || !RespRules.shouldRemind(b.getDeadline(), today)) {
                    continue;
                }
                long days = ChronoUnit.DAYS.between(today, b.getDeadline());
                String title = days < 0 ? "责任书签署已逾期 " + (-days) + " 天：" + b.getTitle()
                        : days == 0 ? "今天截止：请签署《" + b.getTitle() + "》" : days + " 天后截止：请签署《" + b.getTitle() + "》";
                if (messageService.sendOnce("resp:" + b.getId() + ":" + today, Duration.ofDays(2), pending.stream().map(RespSign::getUserId).toList(),
                        MessageDraft.of(days < 0 ? MessageCategory.WARN : MessageCategory.TODO, "RESP", null, title,
                                "请阅读你的岗位安全职责并手写签名，截止 " + b.getDeadline(), RespService.LINK))) {
                    sent.addAndGet(pending.size());
                }
                if (days < 0) {
                    messageService.sendOnce("resp-admin:" + b.getId(), Duration.ofDays(400), messageService.listPermissionUsers(RespService.PERM_EDIT),
                            MessageDraft.of(MessageCategory.WARN, "RESP", null, "《" + b.getTitle() + "》还有 " + pending.size() + " 人没签",
                                    "签署截止日期 " + b.getDeadline() + " 已过，没签的人：" + String.join("、", pending.stream().map(RespSign::getUserName).limit(20).toList()),
                                    RespService.LINK));
                }
            }
        });
        return "提醒 " + sent + " 人次" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
