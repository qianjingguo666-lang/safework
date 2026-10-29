package com.safework.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.tenant.TenantContext;
import com.safework.system.dto.LoginLogQueryDTO;
import com.safework.system.entity.SysLoginLog;
import com.safework.system.mapper.SysLoginLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final SysLoginLogMapper loginLogMapper;

    /** 写日志失败不能影响登录本身 */
    public void record(SysLoginLog entry) {
        try {
            // 登录时还没有租户上下文，切换到日志所属企业再插入
            TenantContext.runAs(entry.getTenantId(), () -> loginLogMapper.insert(entry));
        } catch (Exception e) {
            log.warn("记录登录日志失败：{}", e.getMessage());
        }
    }

    /** 企业管理员查看本企业的登录日志（租户插件自动加条件） */
    public Page<SysLoginLog> page(LoginLogQueryDTO q) {
        return loginLogMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), wrapper(q));
    }

    /** 平台查看全部企业 */
    public IPage<SysLoginLog> pageAllTenants(LoginLogQueryDTO q) {
        LambdaQueryWrapper<SysLoginLog> w = wrapper(q)
                .eq(StringUtils.hasText(q.getTenantCode()), SysLoginLog::getTenantCode, q.getTenantCode());
        return loginLogMapper.selectPageAllTenants(new Page<>(q.getCurrent(), q.getSize()), w);
    }

    public long countTodayAllTenants(int status) {
        return loginLogMapper.countAllTenantsSince(status, LocalDate.now().atStartOfDay());
    }

    private static LambdaQueryWrapper<SysLoginLog> wrapper(LoginLogQueryDTO q) {
        return Wrappers.<SysLoginLog>lambdaQuery()
                .like(StringUtils.hasText(q.getUsername()), SysLoginLog::getUsername, q.getUsername())
                .like(StringUtils.hasText(q.getIp()), SysLoginLog::getIp, q.getIp())
                .eq(q.getStatus() != null, SysLoginLog::getStatus, q.getStatus())
                .ge(q.getBeginTime() != null, SysLoginLog::getLoginTime, q.getBeginTime())
                .le(q.getEndTime() != null, SysLoginLog::getLoginTime, q.getEndTime())
                .orderByDesc(SysLoginLog::getLoginTime);
    }
}
