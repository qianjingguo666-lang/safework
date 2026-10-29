package com.safework.system.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.common.util.IpUtils;
import com.safework.system.dto.LoginDTO;
import com.safework.system.dto.LoginVO;
import com.safework.system.entity.SysLoginLog;
import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysUser;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.mapper.SysUserMapper;
import com.safework.system.security.CaptchaService;
import com.safework.system.security.LoginGuard;
import com.safework.system.security.OnlineUserService;
import com.safework.system.security.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 登录。校验顺序：验证码 → 企业 → 账号锁定 → 用户名密码 → 账号状态。
 * 验证码放在最前面：没有正确验证码的请求连“企业是否存在、账号是否锁定”都探测不到。
 * 每一次登录（成功或失败）都写登录日志。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 登录后租户 ID 存在会话里的 key */
    public static final String SESSION_TENANT_ID = "tenantId";
    /** 昵称也放进会话，记录操作日志时不用再查库 */
    public static final String SESSION_NICKNAME = "nickname";

    private final SysTenantMapper tenantMapper;
    private final SysUserMapper userMapper;
    private final CaptchaService captchaService;
    private final LoginGuard loginGuard;
    private final LoginLogService loginLogService;
    private final OnlineUserService onlineUserService;

    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        String ip = IpUtils.clientIp(request);
        String ua = request.getHeader("User-Agent");
        SysLoginLog entry = new SysLoginLog();
        entry.setTenantId(SysLoginLog.UNKNOWN_TENANT);
        entry.setTenantCode(dto.getTenantCode().trim());
        entry.setUsername(dto.getUsername().trim());
        entry.setIp(ip);
        entry.setBrowser(UserAgentParser.browser(ua));
        entry.setOs(UserAgentParser.os(ua));
        entry.setLoginTime(LocalDateTime.now());
        try {
            LoginVO vo = doLogin(dto, entry);
            entry.setStatus(1);
            entry.setMsg("登录成功");
            return vo;
        } catch (BizException e) {
            entry.setStatus(0);
            entry.setMsg(e.getMessage());
            throw e;
        } finally {
            loginLogService.record(entry);
        }
    }

    private LoginVO doLogin(LoginDTO dto, SysLoginLog entry) {
        // 先查企业只是为了让日志归属到正确的企业，验证码通过之前不暴露任何结果
        SysTenant tenant = tenantMapper.selectOne(Wrappers.<SysTenant>lambdaQuery()
                .eq(SysTenant::getTenantCode, entry.getTenantCode()));
        if (tenant != null) {
            entry.setTenantId(tenant.getId());
        }

        // 1. 验证码（一次性，不管对错都作废）
        captchaService.verify(dto.getCaptchaKey(), dto.getCaptchaCode());

        // 2. 企业
        if (tenant == null) {
            throw new BizException("企业编码不存在");
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new BizException("该企业已停用，请联系平台管理员");
        }
        if (tenant.expired()) {
            throw new BizException("该企业服务已于 " + tenant.getExpireTime().toLocalDate() + " 到期，请联系平台续费");
        }

        // 3. 账号是否因多次输错密码被锁定（在校验密码之前判断，锁定期间密码对了也不让登录）
        String username = entry.getUsername();
        long lockedSeconds = loginGuard.lockedSeconds(tenant.getId(), username);
        if (lockedSeconds > 0) {
            throw new BizException("密码错误次数过多，账号已锁定，请 " + (lockedSeconds + 59) / 60 + " 分钟后再试或联系企业管理员解锁");
        }

        // 4. 在该租户范围内查用户（此时还没登录，临时切换租户上下文）
        SysUser user = TenantContext.callAs(tenant.getId(), () -> userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username)));
        // 用户不存在和密码错误给同样的提示，并且同样计数，避免被枚举用户名
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            int remaining = loginGuard.recordFailure(tenant.getId(), username);
            if (remaining == 0) {
                throw new BizException("密码错误次数过多，账号已锁定 " + loginGuard.lockMinutes() + " 分钟");
            }
            throw new BizException("用户名或密码错误，还可以尝试 " + remaining + " 次");
        }
        entry.setUserId(user.getId());
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已停用");
        }

        // 5. 登录成功：清除失败计数，创建会话，租户 ID 存进会话，后续请求从这里取
        loginGuard.clearFailures(tenant.getId(), username);
        StpUtil.login(user.getId());
        StpUtil.getSession().set(SESSION_TENANT_ID, tenant.getId());
        StpUtil.getSession().set(SESSION_NICKNAME, user.getNickname());
        String token = StpUtil.getTokenValue();

        OnlineUserService.OnlineSession online = new OnlineUserService.OnlineSession();
        online.setToken(token);
        online.setUserId(user.getId());
        online.setUsername(user.getUsername());
        online.setNickname(user.getNickname());
        online.setIp(entry.getIp());
        online.setBrowser(entry.getBrowser());
        online.setOs(entry.getOs());
        onlineUserService.add(tenant.getId(), online);

        return new LoginVO(token, user.getId(), user.getNickname(), tenant.getId(), tenant.getTenantName(),
                TenantContext.PLATFORM_ID.equals(tenant.getId()));
    }

    /** 退出当前会话 */
    public void logout() {
        Object tenantId = StpUtil.getSession().get(SESSION_TENANT_ID);
        if (tenantId != null) {
            onlineUserService.remove(Long.valueOf(tenantId.toString()), StpUtil.getTokenValue());
        }
        StpUtil.logout();
    }
}
