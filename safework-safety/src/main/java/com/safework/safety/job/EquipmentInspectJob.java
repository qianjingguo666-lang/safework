package com.safework.safety.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.safety.entity.Equipment;
import com.safework.safety.equipment.EquipmentRules;
import com.safework.safety.equipment.EquipmentService;
import com.safework.safety.mapper.EquipmentMapper;
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
 * 设备检验到期提醒：剩 30 天、7 天、到期当天各提醒一次；逾期后每 7 天提醒一次。发给设备责任人和有登记检验权限的人。
 */
@Component
@RequiredArgsConstructor
public class EquipmentInspectJob implements ScheduledJob {

    static final String LINK = "/safety/equipment";

    private final TenantJobHelper tenantJobHelper;
    private final EquipmentMapper equipmentMapper;
    private final MessageService messageService;

    @Override
    public String code() {
        return "equipment-inspect";
    }

    @Override
    public String name() {
        return "设备检验到期提醒";
    }

    @Override
    public String cron() {
        return "0 55 8 * * *";
    }

    @Override
    public String description() {
        return "每天 8:55 检查在用设备的定期检验：剩 30 天、7 天、到期当天各提醒一次，逾期后每 7 天提醒一次责任人和安全员";
    }

    @Override
    public String execute() {
        AtomicInteger sent = new AtomicInteger();
        LocalDate today = LocalDate.now();
        List<String> errors = tenantJobHelper.forEachActiveTenant(t -> {
            List<Equipment> list = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                    .eq(Equipment::getStatus, EquipmentRules.IN_USE)
                    .le(Equipment::getNextInspectDate, today.plusDays(30)));
            if (list.isEmpty()) {
                return;
            }
            List<Long> inspectors = messageService.listPermissionUsers(EquipmentService.PERM_INSPECT);
            for (Equipment e : list) {
                if (!EquipmentRules.shouldRemind(e.getNextInspectDate(), today)) {
                    continue;
                }
                long left = ChronoUnit.DAYS.between(today, e.getNextInspectDate());
                String type = EquipmentRules.TYPES.get(e.getEquipType()).label();
                String name = e.getEquipCode() + " " + e.getName() + "（" + type + "）";
                Set<Long> receivers = new LinkedHashSet<>(inspectors);
                if (e.getResponsibleId() != null) {
                    receivers.add(e.getResponsibleId());
                }
                String title = left < 0 ? "设备检验已逾期 " + (-left) + " 天：" + name
                        : left == 0 ? "设备今天到期检验：" + name : "设备 " + left + " 天后到期检验：" + name;
                if (messageService.sendOnce("equip:" + e.getId() + ":" + e.getNextInspectDate() + ":" + left, Duration.ofDays(2), receivers,
                        MessageDraft.of(left <= 7 ? MessageCategory.WARN : MessageCategory.TODO, "EQUIPMENT", e.getId(), title,
                                "应于 " + e.getNextInspectDate() + " 前检验，位置：" + e.getLocation()
                                        + (left < 0 ? "。逾期未检验的设备不能保证安全，请尽快安排检验或停用" : "。请提前联系检验机构"), LINK))) {
                    sent.incrementAndGet();
                }
            }
        });
        return "提醒 " + sent + " 台设备" + (errors.isEmpty() ? "" : "；失败：" + String.join("；", errors));
    }
}
