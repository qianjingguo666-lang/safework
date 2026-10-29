package com.safework.ticket.dashboard;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.common.core.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /** days：统计最近多少天（7～90，默认 30） */
    @SaCheckPermission("dashboard:view")
    @GetMapping("/overview")
    public R<DashboardVO> overview(@RequestParam(defaultValue = "30") int days) {
        return R.ok(dashboardService.overview(days));
    }
}
