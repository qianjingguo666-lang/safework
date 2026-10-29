package com.safework.ticket.service;

import com.safework.system.entity.SysUser;
import com.safework.system.message.MessageCategory;
import com.safework.system.message.MessageDraft;
import com.safework.system.message.MessageService;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.enums.TicketAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 作业票状态变化后给相关人员发消息。在 TicketService.fire()（状态流转唯一入口）里调用，
 * 和业务在同一个事务里：流转失败回滚，消息也不会发出。操作人自己不会收到消息。
 */
@Component
@RequiredArgsConstructor
public class TicketNotifier {

    public static final String BIZ_TYPE = "TICKET";

    private final MessageService messageService;

    public void afterFire(WorkTicket t, TicketAction action, SysUser me, String comment) {
        String name = t.getTicketNo() + " " + t.getTitle();
        String type = t.getTicketType().getLabel();
        switch (action) {
            case SUBMIT -> messageService.sendToPermission(TicketService.PERM_APPROVE, me.getId(), draft(t,
                    MessageCategory.TODO, "待审批：" + name,
                    t.getApplicantName() + " 提交了" + type + "，作业地点 " + t.getLocation() + "，请审核安全措施和气体检测数据"));
            case APPROVE -> {
                send(List.of(t.getApplicantId()), me, draft(t, MessageCategory.NOTICE, "作业票已批准：" + name,
                        withComment(me.getNickname() + " 批准了你的" + type, comment)));
                send(List.of(t.getGuardianId()), me, draft(t, MessageCategory.TODO, "待开工：" + name,
                        "你是这张作业票的监护人，请到现场确认安全措施、复测气体后点击“开始作业”"));
            }
            case REJECT -> send(List.of(t.getApplicantId()), me, draft(t, MessageCategory.TODO, "作业票被驳回：" + name,
                    me.getNickname() + " 驳回了你的" + type + "，原因：" + comment + "。修改后可以重新提交"));
            case START -> send(List.of(t.getApplicantId()), me, draft(t, MessageCategory.NOTICE, "已开工：" + name,
                    "监护人 " + me.getNickname() + " 已确认现场安全措施，作业开始"));
            case COMPLETE -> send(List.of(t.getApplicantId()), me, draft(t, MessageCategory.NOTICE, "已完工：" + name,
                    withComment("监护人 " + me.getNickname() + " 确认作业完成", comment)));
            default -> {
                // 新建、修改、取消不通知
            }
        }
    }

    public static String link(Long ticketId) {
        return "/ticket/list?detail=" + ticketId;
    }

    private void send(List<Long> userIds, SysUser me, MessageDraft draft) {
        List<Long> ids = new ArrayList<>(userIds);
        ids.removeIf(id -> Objects.equals(id, me.getId()));
        messageService.send(ids, draft);
    }

    private static MessageDraft draft(WorkTicket t, String category, String title, String content) {
        return MessageDraft.of(category, BIZ_TYPE, t.getId(), title, content, link(t.getId()));
    }

    private static String withComment(String text, String comment) {
        return StringUtils.hasText(comment) ? text + "，意见：" + comment : text;
    }
}
