package com.safework.system.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.tenant.TenantContext;
import com.safework.system.entity.SysTenant;
import com.safework.system.mapper.SysTenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** 定时任务按企业逐个执行 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantJobHelper {

    private final SysTenantMapper tenantMapper;

    /** 正常使用中的企业（排除运营平台、停用、已到期的） */
    public List<SysTenant> activeTenants() {
        LocalDateTime now = LocalDateTime.now();
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                .ne(SysTenant::getId, TenantContext.PLATFORM_ID)
                .eq(SysTenant::getStatus, 1)
                .and(w -> w.isNull(SysTenant::getExpireTime).or().gt(SysTenant::getExpireTime, now)));
    }

    /**
     * 逐个企业切换租户执行。某个企业出错不影响其他企业，错误汇总返回。
     *
     * @return 出错的企业及原因，全部成功返回空列表
     */
    public List<String> forEachActiveTenant(Consumer<SysTenant> action) {
        List<String> errors = new ArrayList<>();
        for (SysTenant t : activeTenants()) {
            try {
                TenantContext.runAs(t.getId(), () -> action.accept(t));
            } catch (Exception e) {
                log.error("定时任务处理企业 {} 失败", t.getTenantCode(), e);
                errors.add(t.getTenantCode() + "：" + e.getMessage());
            }
        }
        return errors;
    }

    public int activeCount() {
        return activeTenants().size();
    }
}
