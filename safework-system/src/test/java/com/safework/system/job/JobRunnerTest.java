package com.safework.system.job;

import com.safework.common.exception.BizException;
import com.safework.system.mapper.SysJobLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("定时任务：分布式锁和执行记录")
class JobRunnerTest {

    private ValueOperations<String, String> ops;
    private SysJobLogMapper logMapper;
    private final AtomicInteger runs = new AtomicInteger();
    private JobRunner runner;

    /** 测试用任务：第一次成功，name 里带 fail 的会抛异常 */
    private ScheduledJob job(String code, boolean fail) {
        return new ScheduledJob() {
            public String code() { return code; }
            public String name() { return "测试任务"; }
            public String cron() { return "0 0 3 * * *"; }
            public String description() { return "测试"; }
            public String execute() {
                runs.incrementAndGet();
                if (fail) {
                    throw new IllegalStateException("模拟失败");
                }
                return "处理了 3 条";
            }
        };
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        logMapper = mock(SysJobLogMapper.class);
        runner = new JobRunner(List.of(job("ok-job", false), job("fail-job", true)), redis, logMapper, new JobProperties());
    }

    @Test
    @DisplayName("抢到锁：执行任务并记录成功日志")
    void runsWhenLocked() {
        when(ops.setIfAbsent(eq("job:lock:ok-job"), anyString(), any(Duration.class))).thenReturn(true);
        SysJobLog log = runner.runManually("ok-job", "张三");
        assertEquals(1, runs.get());
        assertEquals(1, log.getStatus());
        assertEquals("处理了 3 条", log.getResult());
        assertEquals("MANUAL", log.getTriggerType());
        verify(logMapper).insert(log);
    }

    @Test
    @DisplayName("锁被另一台服务器拿着：不执行，手动执行时提示正在执行")
    void skipsWhenLockedByOthers() {
        when(ops.setIfAbsent(eq("job:lock:ok-job"), anyString(), any(Duration.class))).thenReturn(false);
        BizException e = assertThrows(BizException.class, () -> runner.runManually("ok-job", "张三"));
        assertTrue(e.getMessage().contains("正在执行"));
        assertEquals(0, runs.get());
        verify(logMapper, never()).insert(any(SysJobLog.class));
    }

    @Test
    @DisplayName("任务抛异常：不影响调度器，记录失败日志和原因")
    void recordsFailure() {
        when(ops.setIfAbsent(eq("job:lock:fail-job"), anyString(), any(Duration.class))).thenReturn(true);
        SysJobLog log = runner.runManually("fail-job", null);
        assertEquals(0, log.getStatus());
        assertTrue(log.getResult().contains("模拟失败"));
    }

    @Test
    @DisplayName("任务不存在返回 404")
    void unknownJob() {
        assertEquals(404, assertThrows(BizException.class, () -> runner.runManually("nope", null)).getCode());
    }
}
