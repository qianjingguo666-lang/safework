package com.safework.safety.job;

import com.safework.safety.drill.DrillRules;
import com.safework.safety.drill.DrillService;
import com.safework.safety.drill.DrillVOs;
import com.safework.safety.entity.EmergencyDrill;
import com.safework.system.job.ScheduledJob;
import com.safework.system.job.TenantJobHelper;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** 应急演练提醒：演练前 3 / 1 / 0 天提醒参演人员；过了日期没登记结果、登记后 3 天没评估的提醒演练管理员 */
@Component
@RequiredArgsConstructor
public class DrillRemindJob implements ScheduledJob {

    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final TenantJobHelper tenantJobHelper;
    private final DrillService drillService;
    private final MessageService messageService;

    @Override
    public String code() {
        return "drill-remind";
    }

    @Override
    public String name() {
        return "应急演练提醒";
    }

    @Override
    public String cron() {
        return "0 20 8 * * *";
    }

    @Override
    public String description() {
        return "每天 8:20：演练前 3 / 1 / 0 天提醒参演人员（当天提醒签到）；过了计划日期还没登记结果的、登记结果 3 天还没评估的，提醒演练管理员";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            for (Map.Entry<EmergencyDrill, List<Long>> e : drillService.plannedWithParticipants().entrySet()) {
                EmergencyDrill d = e.getKey();
                if (e.getValue().isEmpty() || !DrillRules.remindParticipants(d.getPlanTime(), today)) {
                    continue;
                }
                boolean isToday = d.getPlanTime().toLocalDate().equals(today);
                String title = (isToday ? "今天有应急演练：" : "应急演练提醒：") + d.getTitle();
                String content = "演练时间 " + d.getPlanTime().format(MDHM) + (d.getLocation() != null ? "，地点 " + d.getLocation() : "")
                        + "，预案《" + d.getPlanTitle() + "》。" + (isToday ? "开始前 2 小时起可以在手机“我的 → 应急演练”签到" : "请提前熟悉处置程序和自己的职责");
                if (messageService.sendOnce("drill-remind:" + d.getId() + ":" + today, Duration.ofDays(2), e.getValue(),
                        MessageDraft.of(MessageCategory.TODO, DrillService.BIZ_TYPE, d.getId(), title, content, "/m/drills"))) {
                    sent.incrementAndGet();
                }
            }
            DrillVOs.Overview o = drillService.overview(today);
            List<EmergencyDrill> late = o.recordLate();
            List<EmergencyDrill> unEval = o.pendingEvaluate().stream()
                    .filter(d -> DrillRules.evaluateLate(d.getStatus(), d.getActualTime(), today)).toList();
            if (late.isEmpty() && unEval.isEmpty()) {
                return;
            }
            List<Long> to = messageService.listPermissionUsers(DrillService.PERM_EDIT);
            StringBuilder sb = new StringBuilder();
            if (!late.isEmpty()) {
                sb.append("过了计划日期还没登记结果：").append(late.stream().limit(5).map(d -> d.getTitle() + "（" + d.getPlanTime().toLocalDate() + "）")
                        .collect(Collectors.joining("、"))).append("。演练了就登记结果，没演练就取消后重新安排。");
            }
            if (!unEval.isEmpty()) {
                sb.append("登记结果后还没评估：").append(unEval.stream().limit(5).map(d -> d.getTitle() + "（" + d.getActualTime().toLocalDate() + "）")
                        .collect(Collectors.joining("、"))).append("。评估要判断预案是否适宜，发现的问题要转整改。");
            }
            if (messageService.sendOnce("drill-manage:" + today, Duration.ofDays(2), to,
                    MessageDraft.of(MessageCategory.TODO, DrillService.BIZ_TYPE, null, "应急演练待处理：" + (late.size() + unEval.size()) + " 次",
                            sb.toString(), DrillService.LINK))) {
                sent.incrementAndGet();
            }
        });
        return "发送提醒 " + sent + " 条" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
