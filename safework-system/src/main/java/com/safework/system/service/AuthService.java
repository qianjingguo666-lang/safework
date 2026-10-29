package com.safework.system.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.dto.LoginDTO;
import com.safework.system.dto.LoginVO;
import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysUser;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** 登录后租户 ID 存在会话里的 key */
    public static final String SESSION_TENANT_ID = "tenantId";
    /** 昵称也放进会话，记录操作日志时不用再查库 */
    public static final String SESSION_NICKNAME = "nickname";

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;

    public LoginVO login(LoginDTO dto) {
        // 1. 校验租户
        SysTenant tenant = tenantMapper.selectOne(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getTenantCode, dto.getTenantCode()));
        if (tenant == null) {
            throw new BizException("企业编码不存在");
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new BizException("该企业已停用");
        }
        if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException("该企业服务已到期");
        }

        // 2. 在该租户范围内查用户（此时还没登录，手动设置租户上下文）
        SysUser user;
        TenantContext.set(tenant.getId());
        try {
            user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getUsername, dto.getUsername()));
        } finally {
            TenantContext.clear();
        }
        // 用户不存在和密码错误给同样的提示，避免被枚举用户名
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用");
        }

        // 3. 登录，并把租户 ID 存进会话，后续请求从这里取
        StpUtil.login(user.getId());
        StpUtil.getSession().set(SESSION_TENANT_ID, tenant.getId());
        StpUtil.getSession().set(SESSION_NICKNAME, user.getNickname());

        return new LoginVO(StpUtil.getTokenValue(), user.getId(), user.getNickname(),
                tenant.getId(), tenant.getTenantName());
    }
}
