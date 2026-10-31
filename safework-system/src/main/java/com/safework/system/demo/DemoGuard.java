package com.safework.system.demo;

import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.entity.SysTenant;
import com.safework.system.service.TenantCache;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 演示模式的判断：只有“演示模式已打开 + 当前用户属于演示企业”时才限制。
 * 平台管理员、其他正式企业不受影响，同一套代码既能做演示站，也能正常使用。
 */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
@RequiredArgsConstructor
public class DemoGuard {

    private final DemoProperties props;
    private final TenantCache tenantCache;

    public DemoProperties props() {
        return props;
    }

    /** 当前请求是不是演示企业的用户（需要租户上下文，未登录的请求为 false） */
    public boolean active() {
        if (!props.isEnabled()) {
            return false;
        }
        Long tenantId = TenantContext.get();
        if (tenantId == null || TenantContext.isPlatform()) {
            return false;
        }
        SysTenant t = tenantCache.getTenant(tenantId);
        return t != null && props.getTenantCodes().contains(t.getTenantCode());
    }

    /** 演示企业不允许做这件事：直接抛 403，提示语说明原因 */
    public void forbid(String what) {
        if (active()) {
            throw new BizException(403, "演示环境不允许" + what + "。这是公开的演示站，任何人都能登录，数据每天 "
                    + props.getResetTime() + " 自动恢复");
        }
    }
}
