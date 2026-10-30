package com.safework.ticket.sign;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.system.entity.SysUser;
import com.safework.ticket.entity.WorkTicketLog;
import com.safework.ticket.mapper.TicketSignatureMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 手写签名：随操作一起提交，和操作记录在同一个事务里保存 */
@Service
@RequiredArgsConstructor
public class SignatureService {

    private final TicketSignatureMapper signatureMapper;

    /** 先校验（在改状态之前调用，签名不合格整个操作不执行） */
    public SignatureImage.Checked check(String dataUrl) {
        if (!StringUtils.hasText(dataUrl)) {
            return null;
        }
        try {
            return SignatureImage.check(dataUrl);
        } catch (IllegalArgumentException e) {
            throw new BizException(e.getMessage());
        }
    }

    public void save(WorkTicketLog log, SysUser signer, SignatureImage.Checked sign) {
        if (sign == null) {
            return;
        }
        TicketSignature s = new TicketSignature();
        s.setTicketId(log.getTicketId());
        s.setLogId(log.getId());
        s.setAction(log.getAction());
        s.setSignerId(signer.getId());
        s.setSignerName(signer.getNickname());
        s.setImage(sign.dataUrl());
        s.setImageHash(sign.sha256());
        signatureMapper.insert(s);
    }

    /** 给操作记录带上签名图片（详情、打印用） */
    public void attach(Long ticketId, List<WorkTicketLog> logs) {
        Map<Long, TicketSignature> byLog = ofTicket(ticketId).stream()
                .collect(Collectors.toMap(TicketSignature::getLogId, Function.identity(), (a, b) -> a));
        logs.forEach(l -> {
            TicketSignature s = byLog.get(l.getId());
            l.setSignImage(s == null ? null : s.getImage());
        });
    }

    public List<TicketSignature> ofTicket(Long ticketId) {
        return signatureMapper.selectList(Wrappers.<TicketSignature>lambdaQuery().eq(TicketSignature::getTicketId, ticketId));
    }
}
