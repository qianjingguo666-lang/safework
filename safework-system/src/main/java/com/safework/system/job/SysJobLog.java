package com.safework.system.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 定时任务执行记录（平台级，不做租户隔离） */
@Data
@TableName("sys_job_log")
public class SysJobLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String jobCode;
    private String jobName;
    /** AUTO 定时触发 / MANUAL 手动执行 */
    private String triggerType;
    private String operatorName;
    /** 1 成功 0 失败 */
    private Integer status;
    private String result;
    private String server;
    private LocalDateTime startTime;
    private Long costMs;
}
