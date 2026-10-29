package com.safework.system.job;

import com.safework.system.mapper.SysJobLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 定时任务调度器：
 * <ul>
 *   <li>自动发现所有 {@link ScheduledJob}，按各自的 cron 注册（application.yml 里可以覆盖 cron）</li>
 *   <li><b>分布式锁</b>：多台服务器同时到点，用 Redis SET NX 抢锁，只有一台执行，避免同一条提醒发两遍；
 *       释放锁用 Lua 脚本“先比较再删除”，防止任务超时后锁被别人拿到，又被自己误删</li>
 *   <li>每次执行记录日志（结果、耗时、哪台服务器），平台页面可以查看、手动执行</li>
 * </ul>
 */
@Slf4j
@Component
public class JobRunner implements SchedulingConfigurer {

    private static final String LOCK_PREFIX = "job:lock:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final List<ScheduledJob> jobs;
    private final StringRedisTemplate redis;
    private final SysJobLogMapper logMapper;
    private final JobProperties props;
    /** 进程标识：主机名@进程号，多实例部署时能看出是哪台执行的 */
    private final String server = ManagementFactory.getRuntimeMXBean().getName();

    public JobRunner(List<ScheduledJob> jobs, StringRedisTemplate redis, SysJobLogMapper logMapper, JobProperties props) {
        this.jobs = jobs.stream().sorted(Comparator.comparing(ScheduledJob::code)).toList();
        this.redis = redis;
        this.logMapper = logMapper;
        this.props = props;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // 默认调度器只有一个线程，一个任务卡住会拖住所有任务（包括消息心跳），所以单独建一个线程池
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("job-");
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
        if (!props.isEnabled()) {
            log.info("定时任务调度已关闭（safework.job.enabled=false），只能手动执行");
            return;
        }
        for (ScheduledJob job : jobs) {
            registrar.addCronTask(() -> {
                try {
                    run(job, "AUTO", null);
                } catch (Exception e) {
                    log.debug("任务 {} 本次未执行：{}", job.code(), e.getMessage());
                }
            }, cronOf(job));
            log.info("注册定时任务 {}（{}），cron = {}", job.name(), job.code(), cronOf(job));
        }
    }

    /** 执行一次任务。抢不到锁时：手动执行抛异常提示，自动执行直接跳过 */
    public SysJobLog run(ScheduledJob job, String triggerType, String operator) {
        String lockKey = LOCK_PREFIX + job.code();
        String lockValue = server + ":" + UUID.randomUUID();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException("任务【" + job.name() + "】正在执行（可能在另一台服务器上），请稍后再试");
        }
        SysJobLog entry = new SysJobLog();
        entry.setJobCode(job.code());
        entry.setJobName(job.name());
        entry.setTriggerType(triggerType);
        entry.setOperatorName(operator);
        entry.setServer(server);
        entry.setStartTime(LocalDateTime.now());
        long start = System.currentTimeMillis();
        try {
            entry.setResult(truncate(job.execute()));
            entry.setStatus(1);
        } catch (Exception e) {
            log.error("定时任务 {} 执行失败", job.code(), e);
            entry.setResult(truncate("执行失败：" + e.getMessage()));
            entry.setStatus(0);
        } finally {
            entry.setCostMs(System.currentTimeMillis() - start);
            redis.execute(UNLOCK, List.of(lockKey), lockValue);
            logMapper.insert(entry);
        }
        return entry;
    }

    public SysJobLog runManually(String code, String operator) {
        return run(find(code), "MANUAL", operator);
    }

    public List<JobVO> list() {
        LocalDateTime now = LocalDateTime.now();
        return jobs.stream().map(job -> {
            JobVO vo = new JobVO();
            vo.setCode(job.code());
            vo.setName(job.name());
            vo.setCron(cronOf(job));
            vo.setDescription(job.description());
            vo.setNextTime(props.isEnabled() ? CronExpression.parse(cronOf(job)).next(now) : null);
            vo.setRunning(Boolean.TRUE.equals(redis.hasKey(LOCK_PREFIX + job.code())));
            vo.setLastLog(logMapper.selectOne(Wrappers.<SysJobLog>lambdaQuery()
                    .eq(SysJobLog::getJobCode, job.code())
                    .orderByDesc(SysJobLog::getStartTime)
                    .last("LIMIT 1")));
            return vo;
        }).toList();
    }

    public Page<SysJobLog> logs(long current, long size, String code, Integer status) {
        return logMapper.selectPage(new Page<>(current, size), Wrappers.<SysJobLog>lambdaQuery()
                .eq(StringUtils.hasText(code), SysJobLog::getJobCode, code)
                .eq(status != null, SysJobLog::getStatus, status)
                .orderByDesc(SysJobLog::getStartTime));
    }

    private ScheduledJob find(String code) {
        return jobs.stream().filter(j -> j.code().equals(code)).findFirst()
                .orElseThrow(() -> new BizException(404, "任务不存在：" + code));
    }

    private String cronOf(ScheduledJob job) {
        String override = props.getCron().get(job.code());
        return StringUtils.hasText(override) ? override : job.cron();
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 997) + "...";
    }
}
