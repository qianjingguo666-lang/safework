package com.safework.system.security;

import com.safework.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("验证码校验")
class CaptchaServiceTest {

    private ValueOperations<String, String> ops;
    private SecurityProperties props;
    private CaptchaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        props = new SecurityProperties();
        service = new CaptchaService(redis, props);
    }

    @Test
    @DisplayName("生成：答案存 Redis 2 分钟；生产环境不返回答案")
    void create() {
        CaptchaVO vo = service.create();
        assertTrue(vo.isEnabled());
        assertNotNull(vo.getCaptchaKey());
        assertNull(vo.getDebugAnswer());
        verify(ops).set(startsWith("captcha:"), anyString(), eq(Duration.ofSeconds(120)));
    }

    @Test
    @DisplayName("开发环境（captcha-debug）返回答案给测试脚本")
    void debugAnswer() {
        props.setCaptchaDebug(true);
        assertNotNull(service.create().getDebugAnswer());
    }

    @Test
    @DisplayName("答案正确通过；取答案用 getAndDelete，同一个验证码只能用一次")
    void correct() {
        when(ops.getAndDelete("captcha:k1")).thenReturn("12");
        assertDoesNotThrow(() -> service.verify("k1", " 12 "));
        verify(ops).getAndDelete("captcha:k1");
    }

    @Test
    @DisplayName("答案错误、已过期、没填，都拒绝")
    void wrong() {
        when(ops.getAndDelete("captcha:k1")).thenReturn("12");
        assertEquals("验证码错误", assertThrows(BizException.class, () -> service.verify("k1", "13")).getMessage());
        when(ops.getAndDelete("captcha:k2")).thenReturn(null);
        assertTrue(assertThrows(BizException.class, () -> service.verify("k2", "1")).getMessage().contains("过期"));
        assertEquals("请输入验证码", assertThrows(BizException.class, () -> service.verify(null, null)).getMessage());
    }

    @Test
    @DisplayName("关闭验证码时不校验")
    void disabled() {
        props.setCaptchaEnabled(false);
        assertDoesNotThrow(() -> service.verify(null, null));
        assertTrue(!service.create().isEnabled());
    }
}
