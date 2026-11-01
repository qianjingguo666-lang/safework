package com.safework.ticket.screen;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "安全数据大屏")
@RestController
@RequestMapping("/screen")
@RequiredArgsConstructor
public class ScreenController {

    private final ScreenService service;

    @SaCheckPermission("screen:view")
    @Operation(summary = "整屏数据：作业中、隐患、风险四色、应急响应、到期预警、最近动态（前端每 30 秒刷新）")
    @GetMapping("/overview")
    public R<ScreenVO> overview() {
        return R.ok(service.overview());
    }
}
