package com.safework.ticket.mobile;

import com.safework.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "手机端")
@RestController
@RequestMapping("/mobile")
@RequiredArgsConstructor
public class MobileController {

    private final MobileService service;

    /** 只要求登录：每一项按各自的权限计算，没有权限的项返回 null */
    @Operation(summary = "手机端首页待办数：待我审批、我的作业、待我整改、待指派 / 待验收、今日巡检、未读消息")
    @GetMapping("/home")
    public R<MobileHomeVO> home() {
        return R.ok(service.home());
    }
}
