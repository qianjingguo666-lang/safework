package com.safework.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import com.safework.common.tenant.TenantContext;
import com.safework.system.security.OnlineUserService;
import com.safework.system.security.OnlineUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 在线用户：企业管理员只能看到、踢出本企业的会话（Redis key 按租户区分） */
@Tag(name = "在线用户", description = "查看、强制下线")
@RestController
@RequestMapping("/system/online")
@RequiredArgsConstructor
public class OnlineUserController {

    private final OnlineUserService onlineUserService;

    @SaCheckPermission("system:online:list")
    @Operation(summary = "在线会话列表")
    @GetMapping("/list")
    public R<List<OnlineUserVO>> list(@RequestParam(required = false) String keyword) {
        List<OnlineUserVO> list = onlineUserService.list(TenantContext.get(), StpUtil.getTokenValue());
        if (StringUtils.hasText(keyword)) {
            list = list.stream().filter(o -> contains(o.getUsername(), keyword) || contains(o.getNickname(), keyword)
                    || contains(o.getIp(), keyword)).toList();
        }
        return R.ok(list);
    }

    @OperLog(module = "在线用户", action = "强制下线")
    @SaCheckPermission("system:online:kickout")
    @Operation(summary = "强制下线")
    @DeleteMapping("/{sessionId}")
    public R<Void> kickout(@PathVariable String sessionId) {
        onlineUserService.kickout(TenantContext.get(), sessionId, StpUtil.getTokenValue());
        return R.ok();
    }

    private static boolean contains(String s, String keyword) {
        return s != null && s.contains(keyword);
    }
}
