package com.safework.system.message;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 通知公告管理（企业管理员） */
@RestController
@RequestMapping("/system/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @SaCheckPermission("system:notice:list")
    @GetMapping("/page")
    public R<Page<SysNotice>> page(@RequestParam(defaultValue = "1") long current,
                                   @RequestParam(defaultValue = "10") long size,
                                   @RequestParam(required = false) String keyword) {
        return R.ok(noticeService.page(current, size, keyword));
    }

    @OperLog(module = "通知公告", action = "发布公告")
    @SaCheckPermission("system:notice:add")
    @PostMapping
    public R<Long> publish(@Valid @RequestBody NoticeSaveDTO dto) {
        return R.ok(noticeService.publish(dto));
    }

    @OperLog(module = "通知公告", action = "撤回公告")
    @SaCheckPermission("system:notice:add")
    @DeleteMapping("/{id}")
    public R<Void> withdraw(@PathVariable Long id) {
        noticeService.withdraw(id);
        return R.ok();
    }
}
