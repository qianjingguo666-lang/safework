package com.safework.ticket.controller;

import com.safework.common.core.R;
import com.safework.ticket.print.TicketPrintService;
import com.safework.ticket.print.TicketVerifyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 扫纸质作业票上的二维码验真（不需要登录，/public/** 在白名单里）。
 * 现场检查人员、承包商负责人用手机扫一下，就能看到这张票在系统里的真实状态。
 */
@Tag(name = "作业票验真", description = "扫纸质作业票上的二维码，不需要登录")
@RestController
@RequestMapping("/public/ticket")
@RequiredArgsConstructor
public class TicketVerifyController {

    private final TicketPrintService printService;

    @Operation(summary = "扫码验真（匿名）：现在这张票能不能作业")
    @GetMapping("/{token}")
    public R<TicketVerifyVO> verify(@PathVariable String token) {
        return R.ok(printService.verify(token));
    }
}
