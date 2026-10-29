package com.safework.system.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * 登录失败锁定：同一企业下同一个用户名，15 分钟内连续输错 5 次密码，锁定 15 分钟。
 * <p>
 * - 按“企业 + 用户名”计数，不管这个用户名存不存在都计数，避免攻击者通过“锁没锁”判断账号是否存在；
 * - 用户名转小写：MySQL 默认排序规则不区分大小写，Admin 和 admin 是同一个账号，不能分开计数被绕过；
 * - 登录成功后清零；企业管理员可以在用户管理里手动解锁。
 */
@Component
@RequiredArgsConstructor
public class LoginGuard {

    private static final String FAIL_PREFIX = "login:fail:";
    private static final String LOCK_PREFIX = "login:lock:";

    private final StringRedisTemplate redis;
    private final SecurityProperties props;

    /** 剩余锁定秒数，0 表示没有锁定 */
    public long lockedSeconds(Long tenantId, String username) {
        Long ttl = redis.getExpire(lockKey(tenantId, username));
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    /**
     * 记一次密码错误。
     *
     * @return 还可以尝试的次数；返回 0 表示这次之后已经被锁定
     */
    public int recordFailure(Long tenantId, String username) {
        String failKey = failKey(tenantId, username);
        Long count = redis.opsForValue().increment(failKey);
        if (count != null && count == 1) {
            redis.expire(failKey, Duration.ofMinutes(props.getFailWindowMinutes()));
        }
        int remaining = props.getMaxFailCount() - (count == null ? 0 : count.intValue());
        if (remaining <= 0) {
            redis.opsForValue().set(lockKey(tenantId, username), "1", Duration.ofMinutes(props.getLockMinutes()));
            redis.delete(failKey);
            return 0;
        }
        return remaining;
    }

    public void clearFailures(Long tenantId, String username) {
        redis.delete(failKey(tenantId, username));
    }

    public void unlock(Long tenantId, String username) {
        redis.delete(failKey(tenantId, username));
        redis.delete(lockKey(tenantId, username));
    }

    public int lockMinutes() {
        return props.getLockMinutes();
    }

    private static String failKey(Long tenantId, String username) {
        return FAIL_PREFIX + tenantId + ":" + normalize(username);
    }

    private static String lockKey(Long tenantId, String username) {
        return LOCK_PREFIX + tenantId + ":" + normalize(username);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
