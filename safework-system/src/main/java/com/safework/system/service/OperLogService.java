package com.safework.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.system.dto.OperLogQueryDTO;
import com.safework.system.entity.SysOperLog;
import com.safework.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OperLogService {

    private final SysOperLogMapper operLogMapper;

    public void save(SysOperLog log) {
        operLogMapper.insert(log);
    }

    public Page<SysOperLog> page(OperLogQueryDTO q) {
        return operLogMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), Wrappers.<SysOperLog>lambdaQuery()
                .eq(StringUtils.hasText(q.getModule()), SysOperLog::getModule, q.getModule())
                .like(StringUtils.hasText(q.getOperatorName()), SysOperLog::getOperatorName, q.getOperatorName())
                .eq(q.getStatus() != null, SysOperLog::getStatus, q.getStatus())
                .eq(StringUtils.hasText(q.getTraceId()), SysOperLog::getTraceId, q.getTraceId())
                .ge(q.getBeginTime() != null, SysOperLog::getCreateTime, q.getBeginTime())
                .le(q.getEndTime() != null, SysOperLog::getCreateTime, q.getEndTime())
                .orderByDesc(SysOperLog::getCreateTime));
    }
}
