package com.safework.system.message;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** 我的消息：登录即可使用，只能看到、操作自己的消息 */
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessagePushService pushService;
    private final NoticeService noticeService;

    /**
     * 实时推送长连接（SSE）。事件：unread（未读数）、message（新消息）。
     * 不写 produces = text/event-stream：未登录等错误要能以普通 JSON 返回给前端
     */
    @GetMapping("/stream")
    public SseEmitter stream() {
        Long me = StpUtil.getLoginIdAsLong();
        return pushService.subscribe(me, messageService.unreadCount(me));
    }

    @GetMapping("/page")
    public R<Page<MessageVO>> page(MessageQueryDTO query) {
        return R.ok(messageService.page(query));
    }

    @GetMapping("/unread")
    public R<Map<String, Long>> unread() {
        return R.ok(messageService.unreadStats());
    }

    @GetMapping("/latest")
    public R<List<MessageVO>> latest() {
        return R.ok(messageService.latestUnread(5));
    }

    @PutMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        messageService.read(id);
        return R.ok();
    }

    /** 全部已读，可以只标记某一类 */
    @PutMapping("/read-all")
    public R<Integer> readAll(@RequestParam(required = false) String category) {
        return R.ok(messageService.readAll(category));
    }

    /** 查看公告全文（收到公告的人都能看） */
    @GetMapping("/notice/{id}")
    public R<SysNotice> notice(@PathVariable Long id) {
        return R.ok(noticeService.getById(id));
    }
}
