package com.safework.system.job;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class JobVO {
    private String code;
    private String name;
    private String cron;
    private String description;
    private LocalDateTime nextTime;
    /** 是否正在执行（分布式锁还在） */
    private boolean running;
    private SysJobLog lastLog;
}
