package com.safework.ticket.service;

import com.safework.common.tenant.TenantContext;
import com.safework.ticket.enums.TicketType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 作业票编号：类型前缀 + 日期 + 当天流水号，如 SX20261028-0001。
 * 用 Redis INCR 生成流水号，原子操作，多个请求同时创建也不会重复。
 */
@Component
@RequiredArgsConstructor
public class TicketNoGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redis;

    public String next(TicketType type) {
        String date = LocalDate.now().format(DATE);
        String key = "safework:ticket-no:" + TenantContext.get() + ":" + type.getPrefix() + ":" + date;
        Long seq = redis.opsForValue().increment(key);
        if (seq != null && seq == 1L) {
            // 只需要保留到第二天，过期自动清理
            redis.expire(key, Duration.ofDays(2));
        }
        return String.format("%s%s-%04d", type.getPrefix(), date, seq);
    }
}
