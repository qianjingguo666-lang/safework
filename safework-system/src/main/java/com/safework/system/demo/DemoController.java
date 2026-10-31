package com.safework.system.demo;

import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "演示模式", description = "登录页展示演示账号、顶部提示")
@RestController
@RequiredArgsConstructor
public class DemoController {

    private final DemoGuard guard;

    @Operation(summary = "演示信息（匿名）：是否演示站、演示账号、数据恢复时间")
    @GetMapping("/public/demo")
    public R<DemoInfoVO> info() {
        DemoProperties p = guard.props();
        boolean show = p.isEnabled() || p.isShowAccounts();
        return R.ok(new DemoInfoVO(p.isEnabled(), p.isEnabled() ? p.getTenantCodes() : List.of(), p.getResetTime(), p.getAiDailyLimit(),
                show ? p.getPassword() : null, !show ? List.of() : p.isEnabled() ? p.getAccounts().stream().filter(a -> !"platform".equals(a.getTenantCode())).toList() : p.getAccounts()));
    }
}
