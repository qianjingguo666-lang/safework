package com.safework.safety.job;

import com.safework.safety.point.PointRules;
import com.safework.safety.point.PointService;
import com.safework.safety.point.PointVOs;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** 隐患随手拍月榜：每月 1 日给全体员工公布上月积分前三名（上月没人得分不发） */
@Component
@RequiredArgsConstructor
public class PointMonthlyJob implements ScheduledJob {

    private final TenantJobHelper tenantJobHelper;
    private final PointService pointService;
    private final MessageService messageService;
    private final UserService userService;

    @Override
    public String code() {
        return "point-monthly";
    }

    @Override
    public String name() {
        return "隐患随手拍月榜";
    }

    @Override
    public String cron() {
        return "0 40 9 1 * *";
    }

    @Override
    public String description() {
        return "每月 1 日 9:40 给全体员工公布上月隐患随手拍积分前三名，鼓励大家发现和报告隐患";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate last = LocalDate.now().minusMonths(1);
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            PointVOs.Ranking r = pointService.ranking(PointRules.MONTH, last.getYear(), last.getMonthValue());
            if (r.rows().isEmpty()) {
                return;
            }
            String top = r.rows().stream().filter(x -> x.rank() <= 3)
                    .map(x -> "第 " + x.rank() + " 名 " + x.userName() + "（" + x.points() + " 分，确认隐患 " + x.hazards() + " 条）")
                    .collect(Collectors.joining("；"));
            if (messageService.sendOnce("point-monthly:" + last, Duration.ofDays(20), userService.listAllActiveIds(),
                    MessageDraft.of(MessageCategory.NOTICE, PointService.BIZ_TYPE, null, r.label() + "隐患随手拍积分榜",
                            top + "。上月共确认隐患 " + r.totalHazards() + " 条，发现隐患随手拍，积分可以兑换奖品", PointService.MOBILE_LINK))) {
                sent.incrementAndGet();
            }
        });
        return "发送月榜 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
