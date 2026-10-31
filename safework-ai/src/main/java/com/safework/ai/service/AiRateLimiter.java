package com.safework.ai.service;

import com.safework.ai.config.AiProperties;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.demo.DemoGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 简单限流：每人每分钟最多 N 次（固定窗口计数）。
 * 大模型按调用量收费，不限流的话一个脚本就能把额度刷光。
 * 演示模式下，演示企业还有每天的总次数上限（所有人共用演示账号，按人限流不够）。
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    private final StringRedisTemplate redis;
    private final AiProperties props;
    private final DemoGuard demoGuard;

    public void check(Long userId) {
        long minute = System.currentTimeMillis() / 60_000;
        String key = "safework:ai:rate:" + userId + ":" + minute;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(70));
        }
        if (count != null && count > props.getRateLimitPerMinute()) {
            throw new BizException(429, "提问太频繁，请一分钟后再试");
        }
        checkDemoDaily();
    }

    /** 演示企业每天的 AI 总次数，超过就提示明天再来（保护大模型和语音识别的余额） */
    private void checkDemoDaily() {
        int limit = demoGuard.props().getAiDailyLimit();
        if (limit <= 0 || !demoGuard.active()) {
            return;
        }
        String key = "safework:ai:demo-daily:" + TenantContext.get() + ":" + LocalDate.now();
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        if (count != null && count > limit) {
            throw new BizException(429, "演示环境今天的 AI 体验次数（" + limit + " 次）已经用完了，明天再来试试");
        }
    }
}
