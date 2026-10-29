package com.safework.system.job;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** application.yml 里的 safework.job */
@Data
@Component
@ConfigurationProperties(prefix = "safework.job")
public class JobProperties {
    /** 是否开启定时调度（关闭后仍然可以在平台页面手动执行） */
    private boolean enabled = true;
    /** 覆盖默认的 cron，key 是任务编码，如 hazard-deadline: "0 0 9 * * *" */
    private Map<String, String> cron = new HashMap<>();
    /** 登录日志保留天数 */
    private int loginLogDays = 180;
    /** 已读消息保留天数 */
    private int readMessageDays = 90;
    /** 任务执行记录保留天数 */
    private int jobLogDays = 30;
}
