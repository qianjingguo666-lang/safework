package com.safework.ticket.print;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.entity.SysTenant;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.service.AuthService;
import com.safework.ticket.dto.TicketDetailVO;
import com.safework.ticket.entity.WorkTicket;
import com.safework.ticket.entity.WorkTicketLog;
import com.safework.ticket.enums.TicketAction;
import com.safework.ticket.mapper.WorkTicketLogMapper;
import com.safework.ticket.mapper.WorkTicketMapper;
import com.safework.ticket.service.TicketService;
import com.safework.ticket.sign.SignatureService;
import com.safework.ticket.sign.TicketSignature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 作业票打印与扫码验真：
 * <pre>
 * 打印：详情（同样的数据权限）+ 每一步的手写签名 + 验真二维码，前端排成 A4 票面，浏览器打印或另存为 PDF
 * 验真：现场检查人员扫纸质票上的二维码（不用登录），看到系统里这张票现在的真实状态，
 *      纸质票被涂改、复印、过期继续使用，一扫就能发现
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class TicketPrintService {

    private static final Pattern TOKEN = Pattern.compile("[0-9a-f]{32}");

    private final TicketService ticketService;
    private final WorkTicketMapper ticketMapper;
    private final WorkTicketLogMapper logMapper;
    private final SignatureService signatureService;
    private final SysTenantMapper tenantMapper;

    public TicketPrintVO print(Long id) {
        // 复用详情：数据权限（没有权限返回 404）、操作记录、签名、审批进度都在里面
        TicketDetailVO d = ticketService.detail(id);
        WorkTicket t = d.getTicket();
        Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
        SysTenant tenant = tenantMapper.selectById(TenantContext.get());
        LocalDateTime now = LocalDateTime.now();
        return new TicketPrintVO(t, d.getLogs(), d.getApproval(), tenant == null ? null : tenant.getTenantName(),
                ensureToken(t), TicketVerdict.of(t.getStatus(), t.getPlanEndTime(), now),
                nickname == null ? null : nickname.toString(), now);
    }

    /** 第一次打印时生成验真码。用条件更新（verify_token 为空才写），两个人同时打印也只会生成一个 */
    private String ensureToken(WorkTicket t) {
        if (t.getVerifyToken() != null) {
            return t.getVerifyToken();
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        // 不用 updateById：不改乐观锁版本号，打印不能让正在进行的审批失败
        ticketMapper.update(null, Wrappers.<WorkTicket>lambdaUpdate()
                .set(WorkTicket::getVerifyToken, token)
                .eq(WorkTicket::getId, t.getId())
                .isNull(WorkTicket::getVerifyToken));
        return ticketMapper.selectById(t.getId()).getVerifyToken();
    }

    /** 扫码验真（匿名） */
    public TicketVerifyVO verify(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new BizException(404, "二维码无效");
        }
        Long tenantId = ticketMapper.selectTenantIdByVerifyToken(token);
        if (tenantId == null) {
            throw new BizException(404, "作业票不存在，或者二维码不是本系统打印的");
        }
        return TenantContext.callAs(tenantId, () -> {
            WorkTicket t = ticketMapper.selectOne(Wrappers.<WorkTicket>lambdaQuery().eq(WorkTicket::getVerifyToken, token));
            SysTenant tenant = tenantMapper.selectById(tenantId);
            List<WorkTicketLog> logs = logMapper.selectList(Wrappers.<WorkTicketLog>lambdaQuery()
                    .eq(WorkTicketLog::getTicketId, t.getId())
                    .in(WorkTicketLog::getAction, TicketAction.SUBMIT, TicketAction.APPROVE, TicketAction.REJECT,
                            TicketAction.START, TicketAction.COMPLETE, TicketAction.CANCEL)
                    .orderByAsc(WorkTicketLog::getCreateTime)
                    .orderByAsc(WorkTicketLog::getId));
            Set<Long> signed = signatureService.ofTicket(t.getId()).stream().map(TicketSignature::getLogId).collect(Collectors.toSet());
            List<TicketVerifyVO.Step> steps = logs.stream()
                    .map(l -> new TicketVerifyVO.Step(l.getAction().getLabel(), l.getOperatorName(), signed.contains(l.getId()), l.getCreateTime()))
                    .toList();
            LocalDateTime now = LocalDateTime.now();
            return new TicketVerifyVO(tenant == null ? null : tenant.getTenantName(), t.getTicketNo(), t.getTicketType().getLabel(),
                    t.getTitle(), t.getLocation(), t.getSpaceName(), t.getStatus().name(), t.getStatus().getLabel(),
                    TicketVerdict.of(t.getStatus(), t.getPlanEndTime(), now), t.getApplicantName(), t.getGuardianName(),
                    t.getPlanStartTime(), t.getPlanEndTime(), t.getActualStartTime(), t.getActualEndTime(),
                    t.getGasOxygen(), t.getGasLel(), t.getGasH2s(), t.getGasCheckTime(), steps, now);
        });
    }
}
