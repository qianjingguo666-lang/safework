package com.safework.safety.service;

import com.safework.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** 通用编号：前缀 + 日期 + 当天流水号，如 YH20261028-0001。Redis INCR 保证并发不重复 */
@Component
@RequiredArgsConstructor
public class SerialNoGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;

    public String next(String prefix) {
        String date = LocalDate.now().format(DATE);
        String key = "safework:serial-no:" + TenantContext.get() + ":" + prefix + ":" + date;
        Long seq = redis.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            redis.expire(key, Duration.ofDays(2));
        }
        return String.format("%s%s-%04d", prefix, date, seq);
    }
}
