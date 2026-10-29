package com.safework.safety.service;

import com.safework.safety.entity.Hazard;
import com.safework.safety.enums.HazardAction;
import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 隐患闭环各环节的消息通知（和作业票一样，在状态流转里调用，同一个事务） */
@Component
@RequiredArgsConstructor
public class HazardNotifier {

    public static final String BIZ_TYPE = "HAZARD";
    public static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MessageService messageService;

    public void afterFire(Hazard h, HazardAction action, SysUser me, String comment) {
        String name = h.getHazardNo() + " " + h.getTitle();
        boolean major = "MAJOR".equals(h.getLevel());
        switch (action) {
            // 重大隐患用“预警”类别，前端会弹出醒目提醒
            case REPORT -> messageService.sendToPermission(HazardService.PERM_HANDLE, me.getId(), draft(h,
                    major ? MessageCategory.WARN : MessageCategory.TODO,
                    (major ? "【重大隐患】待指派：" : "新隐患待指派：") + name,
                    me.getNickname() + " 在 " + h.getLocation() + " 发现" + h.getCategory() + "类隐患，请指派整改责任人"));
            case ASSIGN -> send(List.of(h.getRectifierId()), me, draft(h, MessageCategory.TODO, "待整改：" + name,
                    withComment(me.getNickname() + " 指派你负责整改，期限 " + h.getDeadline().format(FMT), comment)));
            case REVOKE -> send(List.of(h.getReporterId()), me, draft(h, MessageCategory.NOTICE, "隐患已撤销：" + name,
                    me.getNickname() + " 撤销了你上报的隐患，原因：" + comment));
            case RECTIFY -> {
                List<Long> verifiers = new ArrayList<>(messageService.listPermissionUsers(HazardService.PERM_HANDLE));
                // 整改人不能验收自己的整改，所以也不给他发“待验收”
                verifiers.remove(h.getRectifierId());
                send(verifiers, me, draft(h, MessageCategory.TODO, "待验收：" + name,
                        me.getNickname() + " 已完成整改：" + h.getRectifyMeasure()));
            }
            case VERIFY_PASS -> send(List.of(h.getReporterId(), h.getRectifierId()), me, draft(h, MessageCategory.NOTICE,
                    "隐患已闭环：" + name, withComment(me.getNickname() + " 验收通过", comment)));
            case VERIFY_FAIL -> send(List.of(h.getRectifierId()), me, draft(h, MessageCategory.TODO, "验收不通过，请重新整改：" + name,
                    me.getNickname() + " 验收不通过，原因：" + comment));
            default -> {
            }
        }
    }

    public static String link(Long hazardId) {
        return "/safety/hazard?detail=" + hazardId;
    }

    private void send(List<Long> userIds, SysUser me, MessageDraft draft) {
        List<Long> ids = new ArrayList<>(userIds);
        ids.removeIf(id -> id == null || Objects.equals(id, me.getId()));
        messageService.send(ids, draft);
    }

    private static MessageDraft draft(Hazard h, String category, String title, String content) {
        return MessageDraft.of(category, BIZ_TYPE, h.getId(), title, content, link(h.getId()));
    }

    private static String withComment(String text, String comment) {
        return StringUtils.hasText(comment) ? text + "，说明：" + comment : text;
    }
}
