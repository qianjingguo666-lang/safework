package com.safework.ticket.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.core.R;
import com.safework.ticket.dto.TicketActionDTO;
import com.safework.ticket.dto.TicketDetailVO;
import com.safework.ticket.dto.TicketQueryDTO;
import com.safework.ticket.dto.TicketSaveDTO;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作业票接口。接口上的注解只控制“有没有这类功能权限”，
 * 具体“是不是这张票的申请人 / 监护人”在 Service 里判断。
 */
@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @SaCheckPermission("ticket:list")
    @GetMapping("/page")
    public R<Page<WorkTicket>> page(TicketQueryDTO query) {
        return R.ok(ticketService.page(query));
    }

    @SaCheckPermission("ticket:list")
    @GetMapping("/{id}")
    public R<TicketDetailVO> detail(@PathVariable Long id) {
        return R.ok(ticketService.detail(id));
    }

    @SaCheckPermission("ticket:add")
    @PostMapping
    public R<Long> create(@Valid @RequestBody TicketSaveDTO dto) {
        return R.ok(ticketService.create(dto));
    }

    @SaCheckPermission("ticket:add")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TicketSaveDTO dto) {
        ticketService.update(id, dto);
        return R.ok();
    }

    @SaCheckPermission("ticket:add")
    @PostMapping("/{id}/submit")
    public R<Void> submit(@PathVariable Long id) {
        ticketService.submit(id);
        return R.ok();
    }

    @SaCheckPermission("ticket:add")
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestBody(required = false) TicketActionDTO dto) {
        ticketService.cancel(id, orEmpty(dto));
        return R.ok();
    }

    @SaCheckPermission("ticket:approve")
    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) TicketActionDTO dto) {
        ticketService.approve(id, orEmpty(dto));
        return R.ok();
    }

    @SaCheckPermission("ticket:approve")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) TicketActionDTO dto) {
        ticketService.reject(id, orEmpty(dto));
        return R.ok();
    }

    @SaCheckPermission("ticket:list")
    @PostMapping("/{id}/start")
    public R<Void> start(@PathVariable Long id, @RequestBody(required = false) TicketActionDTO dto) {
        ticketService.start(id, orEmpty(dto));
        return R.ok();
    }

    @SaCheckPermission("ticket:list")
    @PostMapping("/{id}/complete")
    public R<Void> complete(@PathVariable Long id, @RequestBody(required = false) TicketActionDTO dto) {
        ticketService.complete(id, orEmpty(dto));
        return R.ok();
    }

    private static TicketActionDTO orEmpty(TicketActionDTO dto) {
        return dto == null ? new TicketActionDTO() : dto;
    }
}
