package com.safework.system.message;

import com.safework.system.demo.DemoGuard;
import com.safework.system.mapper.SysNoticeMapper;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.system.service.AuthService;
import com.safework.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 通知公告：发布 = 保存公告 + 给企业全员各发一条消息（同一个事务） */
@Service
@RequiredArgsConstructor
public class NoticeService {

    public static final String BIZ_TYPE = "NOTICE";

    private final SysNoticeMapper noticeMapper;
    private final MessageService messageService;
    private final UserService userService;
    private final DemoGuard demoGuard;

    public Page<SysNotice> page(long current, long size, String keyword) {
        return noticeMapper.selectPage(new Page<>(current, size), Wrappers.<SysNotice>lambdaQuery()
                .like(StringUtils.hasText(keyword), SysNotice::getTitle, keyword)
                .orderByDesc(SysNotice::getCreateTime));
    }

    public SysNotice getById(Long id) {
        SysNotice n = noticeMapper.selectById(id);
        if (n == null) {
            throw new BizException(404, "公告不存在或已撤回");
        }
        return n;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long publish(NoticeSaveDTO dto) {
        // 演示企业不能发全员公告（接口和 AI 智能体的“发布公告”都走这里）
        demoGuard.forbid("发布全员通知公告");
        List<Long> receivers = userService.listAllActiveIds();
        SysNotice n = new SysNotice();
        n.setTitle(dto.getTitle().trim());
        n.setContent(dto.getContent());
        n.setLevel(StringUtils.hasText(dto.getLevel()) ? dto.getLevel() : "NORMAL");
        n.setPublisherId(StpUtil.getLoginIdAsLong());
        Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
        n.setPublisherName(nickname == null ? null : nickname.toString());
        n.setReceiverCount(receivers.size());
        noticeMapper.insert(n);
        String prefix = "IMPORTANT".equals(n.getLevel()) ? "【重要】" : "";
        messageService.send(receivers, MessageDraft.of(MessageCategory.ANNOUNCE, BIZ_TYPE, n.getId(),
                prefix + n.getTitle(), plainSummary(n.getContent()), null));
        return n.getId();
    }

    /** 撤回：删除公告和已经发出的消息 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        getById(id);
        noticeMapper.deleteById(id);
        messageService.removeByBiz(BIZ_TYPE, id);
    }

    private static String plainSummary(String content) {
        String s = content.replaceAll("\\s+", " ").trim();
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }
}
