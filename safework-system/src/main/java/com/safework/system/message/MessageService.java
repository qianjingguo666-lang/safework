package com.safework.system.message;

import com.safework.system.mapper.SysMessageMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.service.AuthService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 站内消息。
 * <p>
 * 发送时机：业务代码在自己的事务里调用 {@link #send}，消息和业务数据<b>一起提交、一起回滚</b>
 * （审批失败了，“已批准”的消息也不会存在）；实时推送放在<b>事务提交之后</b>，
 * 避免用户收到推送点进去却看到旧状态（事务还没提交）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final String ONCE_PREFIX = "message:once:";

    private final SysMessageMapper messageMapper;
    private final MessagePushService pushService;
    private final UserService userService;
    private final StringRedisTemplate redis;

    // ======================== 发送 ========================

    /** 发给指定用户（当前租户下）。空 ID、重复 ID 自动去掉 */
    public int send(Collection<Long> userIds, MessageDraft draft) {
        return doSend(userIds, draft, currentNickname());
    }

    private int doSend(Collection<Long> userIds, MessageDraft draft, String sender) {
        Set<Long> receivers = new LinkedHashSet<>();
        userIds.stream().filter(Objects::nonNull).forEach(receivers::add);
        if (receivers.isEmpty()) {
            return 0;
        }
        if (TenantContext.get() == null) {
            throw new IllegalStateException("发送消息必须在租户上下文中（定时任务请用 TenantContext.runAs）");
        }
        List<SysMessage> saved = new ArrayList<>();
        for (Long userId : receivers) {
            SysMessage m = new SysMessage();
            m.setUserId(userId);
            m.setCategory(draft.category());
            m.setBizType(draft.bizType());
            m.setBizId(draft.bizId());
            m.setTitle(draft.title());
            m.setContent(draft.content());
            m.setLink(draft.link());
            m.setSenderName(sender);
            m.setReadFlag(0);
            messageMapper.insert(m);
            saved.add(m);
        }
        afterCommit(() -> saved.forEach(m -> pushService.publish(m.getUserId(), "message", MessageVO.of(m))));
        return saved.size();
    }

    /** 发给当前企业里拥有某个权限的人（如 ticket:approve = 所有审批人），可以排除操作人自己 */
    public int sendToPermission(String perm, Long excludeUserId, MessageDraft draft) {
        List<Long> ids = new ArrayList<>(userService.listUserIdsWithPermission(perm));
        ids.remove(excludeUserId);
        return send(ids, draft);
    }

    /** 当前企业拥有某个权限的用户 ID */
    public List<Long> listPermissionUsers(String perm) {
        return userService.listUserIdsWithPermission(perm);
    }

    /**
     * 同一件事只提醒一次（定时任务用，发送人显示为“系统”）：例如同一条隐患每天只提醒一次逾期，手动多执行几次任务也不会重复发。
     *
     * @return 这次是否真的发送了
     */
    public boolean sendOnce(String dedupeKey, Duration window, Collection<Long> userIds, MessageDraft draft) {
        Boolean first = redis.opsForValue().setIfAbsent(ONCE_PREFIX + TenantContext.get() + ":" + dedupeKey, "1", window);
        if (!Boolean.TRUE.equals(first)) {
            return false;
        }
        return doSend(userIds, draft, null) > 0;
    }

    // ======================== 查询（只能看自己的） ========================

    public Page<MessageVO> page(MessageQueryDTO q) {
        Page<SysMessage> page = messageMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()),
                Wrappers.<SysMessage>lambdaQuery()
                        .eq(SysMessage::getUserId, StpUtil.getLoginIdAsLong())
                        .eq(StringUtils.hasText(q.getCategory()), SysMessage::getCategory, q.getCategory())
                        .eq(q.getReadFlag() != null, SysMessage::getReadFlag, q.getReadFlag())
                        .orderByDesc(SysMessage::getCreateTime));
        Page<MessageVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(MessageVO::of).toList());
        return result;
    }

    /** 未读数：总数 + 按分类 */
    public Map<String, Long> unreadStats() {
        Long me = StpUtil.getLoginIdAsLong();
        long todo = countUnread(me, MessageCategory.TODO);
        long notice = countUnread(me, MessageCategory.NOTICE);
        long warn = countUnread(me, MessageCategory.WARN);
        long announce = countUnread(me, MessageCategory.ANNOUNCE);
        return Map.of("total", todo + notice + warn + announce, "TODO", todo, "NOTICE", notice,
                "WARN", warn, "ANNOUNCE", announce);
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getReadFlag, 0));
    }

    /** 小铃铛下拉：最近 5 条未读 */
    public List<MessageVO> latestUnread(int limit) {
        return messageMapper.selectPage(new Page<>(1, limit, false), Wrappers.<SysMessage>lambdaQuery()
                        .eq(SysMessage::getUserId, StpUtil.getLoginIdAsLong())
                        .eq(SysMessage::getReadFlag, 0)
                        .orderByDesc(SysMessage::getCreateTime))
                .getRecords().stream().map(MessageVO::of).toList();
    }

    public void read(Long id) {
        // 条件里带上 user_id：只能标记自己的消息（加上租户插件，别的企业、别人的消息都改不了）
        int rows = messageMapper.update(null, Wrappers.<SysMessage>lambdaUpdate()
                .eq(SysMessage::getId, id)
                .eq(SysMessage::getUserId, StpUtil.getLoginIdAsLong())
                .set(SysMessage::getReadFlag, 1)
                .set(SysMessage::getReadTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(404, "消息不存在");
        }
        pushUnread();
    }

    public int readAll(String category) {
        int rows = messageMapper.update(null, Wrappers.<SysMessage>lambdaUpdate()
                .eq(SysMessage::getUserId, StpUtil.getLoginIdAsLong())
                .eq(SysMessage::getReadFlag, 0)
                .eq(StringUtils.hasText(category), SysMessage::getCategory, category)
                .set(SysMessage::getReadFlag, 1)
                .set(SysMessage::getReadTime, LocalDateTime.now()));
        pushUnread();
        return rows;
    }

    /** 撤回某个业务产生的全部消息（如撤回公告） */
    public int removeByBiz(String bizType, Long bizId) {
        return messageMapper.delete(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getBizType, bizType)
                .eq(SysMessage::getBizId, bizId));
    }

    public int cleanReadBefore(LocalDateTime before) {
        return messageMapper.deleteReadBefore(before);
    }

    // ======================== 内部方法 ========================

    /** 在其他标签页同步未读数（例如在消息中心点了已读，另一个标签页的小红点也要变） */
    private void pushUnread() {
        Long me = StpUtil.getLoginIdAsLong();
        pushService.publish(me, "unread", Map.of("count", unreadCount(me)));
    }

    private long countUnread(Long userId, String category) {
        return messageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getReadFlag, 0)
                .eq(SysMessage::getCategory, category));
    }

    private static String currentNickname() {
        try {
            if (StpUtil.isLogin()) {
                Object name = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
                return name == null ? null : name.toString();
            }
        } catch (Exception ignored) {
            // 定时任务等没有 Web 请求的线程里调用 StpUtil 会抛异常，当作系统发送
        }
        return null;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
