package com.safework.ai.service;

import com.safework.ai.config.AiProperties;
import com.safework.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 简单限流：每人每分钟最多 N 次（固定窗口计数）。
 * 大模型按调用量收费，不限流的话一个脚本就能把额度刷光。
 */
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    private final StringRedisTemplate redis;
    private final AiProperties props;

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
    }
}
