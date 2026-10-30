package com.safework.system.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 用 Mockito 模拟 Redis，不需要真的启动 Redis 就能测试锁定逻辑 */
@DisplayName("登录失败锁定")
class LoginGuardTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private LoginGuard guard;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        guard = new LoginGuard(redis, new SecurityProperties());
    }

    @Test
    @DisplayName("第一次输错：开始计数，设置 15 分钟窗口，还剩 4 次")
    void firstFailure() {
        when(ops.increment("login:fail:1:admin")).thenReturn(1L);
        assertEquals(4, guard.recordFailure(1L, "admin"));
        verify(redis).expire(eq("login:fail:1:admin"), eq(Duration.ofMinutes(15)));
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("用户名转小写计数：Admin 和 admin 是同一个账号")
    void caseInsensitive() {
        when(ops.increment("login:fail:1:admin")).thenReturn(2L);
        assertEquals(3, guard.recordFailure(1L, " Admin "));
    }

    @Test
    @DisplayName("第 5 次输错：锁定 15 分钟，清掉计数")
    void lockAtFifth() {
        when(ops.increment(anyString())).thenReturn(5L);
        assertEquals(0, guard.recordFailure(1L, "admin"));
        verify(ops).set("login:lock:1:admin", "1", Duration.ofMinutes(15));
        verify(redis).delete("login:fail:1:admin");
    }

    @Test
    @DisplayName("剩余锁定时间：没有锁定时 Redis 返回 -2，按 0 处理")
    void lockedSeconds() {
        when(redis.getExpire("login:lock:1:admin")).thenReturn(-2L);
        assertEquals(0, guard.lockedSeconds(1L, "admin"));
        when(redis.getExpire("login:lock:1:admin")).thenReturn(600L);
        assertEquals(600, guard.lockedSeconds(1L, "admin"));
    }

    @Test
    @DisplayName("不同企业的同名账号分开计数")
    void tenantsSeparated() {
        when(ops.increment("login:fail:2:admin")).thenReturn(1L);
        assertEquals(4, guard.recordFailure(2L, "admin"));
        verify(ops, never()).increment("login:fail:1:admin");
    }
}
