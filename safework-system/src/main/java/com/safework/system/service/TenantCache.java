package com.safework.system.service;

import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysTenantPackage;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.mapper.SysTenantPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 企业、套餐的本地缓存（30 秒）。
 * 每个请求都要检查“企业是否停用/到期”、计算权限时要用到套餐，不缓存的话每个请求多查 2 次数据库。
 * 平台修改企业或套餐时会主动清除缓存，所以本机马上生效；多台服务器部署时其他机器最多延迟 30 秒。
 */
@Component
@RequiredArgsConstructor
public class TenantCache {

    private static final long TTL_MILLIS = 30_000;

    private record Entry<T>(T value, long loadedAt) {
        boolean fresh() {
            return System.currentTimeMillis() - loadedAt < TTL_MILLIS;
        }
    }

    private final SysTenantMapper tenantMapper;
    private final SysTenantPackageMapper packageMapper;
    private final Map<Long, Entry<SysTenant>> tenants = new ConcurrentHashMap<>();
    private final Map<Long, Entry<SysTenantPackage>> packages = new ConcurrentHashMap<>();

    /** sys_tenant、sys_tenant_package 都不做租户隔离，任何租户上下文下都能查 */
    public SysTenant getTenant(Long id) {
        return get(tenants, id, tenantMapper::selectById);
    }

    public SysTenantPackage getPackage(Long id) {
        return get(packages, id, packageMapper::selectById);
    }

    public void evictTenant(Long id) {
        tenants.remove(id);
    }

    public void evictPackage(Long id) {
        packages.remove(id);
    }

    private static <T> T get(Map<Long, Entry<T>> cache, Long id, Function<Long, T> loader) {
        if (id == null) {
            return null;
        }
        Entry<T> e = cache.get(id);
        if (e == null || !e.fresh()) {
            // 查不到也缓存（value 为 null），防止不存在的 ID 反复打到数据库
            e = new Entry<>(loader.apply(id), System.currentTimeMillis());
            cache.put(id, e);
        }
        return e.value();
    }
}
