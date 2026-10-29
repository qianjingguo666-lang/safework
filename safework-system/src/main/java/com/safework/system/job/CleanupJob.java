package com.safework.system.job;

import com.safework.system.mapper.SysJobLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.system.mapper.SysLoginLogMapper;
import com.safework.system.message.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 数据清理：日志、已读消息不能无限增长，按保留天数删除（天数在 application.yml 的 safework.job 下配置） */
@Component
@RequiredArgsConstructor
public class CleanupJob implements ScheduledJob {

    private final SysLoginLogMapper loginLogMapper;
    private final SysJobLogMapper jobLogMapper;
    private final MessageService messageService;
    private final JobProperties props;

    @Override
    public String code() {
        return "data-cleanup";
    }

    @Override
    public String name() {
        return "过期数据清理";
    }

    @Override
    public String cron() {
        return "0 0 3 * * *";
    }

    @Override
    public String description() {
        return "每天凌晨 3:00 删除 " + props.getLoginLogDays() + " 天前的登录日志、" + props.getReadMessageDays()
                + " 天前的已读消息、" + props.getJobLogDays() + " 天前的任务执行记录";
    }

    @Override
    public String execute() {
        LocalDateTime now = LocalDateTime.now();
        int logins = loginLogMapper.deleteBefore(now.minusDays(props.getLoginLogDays()));
        int messages = messageService.cleanReadBefore(now.minusDays(props.getReadMessageDays()));
        int jobs = jobLogMapper.delete(Wrappers.<SysJobLog>lambdaQuery()
                .lt(SysJobLog::getStartTime, now.minusDays(props.getJobLogDays())));
        return "删除登录日志 " + logins + " 条、已读消息 " + messages + " 条、任务记录 " + jobs + " 条";
    }
}
