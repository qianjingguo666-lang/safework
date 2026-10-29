package com.safework.system.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全配置（application.yml 里的 safework.security）
 */
@Data
@Component
@ConfigurationProperties(prefix = "safework.security")
public class SecurityProperties {

    /** 是否开启图形验证码 */
    private boolean captchaEnabled = true;

    /**
     * 开发环境专用：获取验证码时把答案一起返回，方便 PowerShell 测试脚本自动登录。
     * 只在 application-dev.yml 里打开，生产环境（prod）必须是 false。
     */
    private boolean captchaDebug = false;

    /** 验证码有效期（秒） */
    private int captchaExpireSeconds = 120;

    /** 连续输错密码多少次后锁定账号 */
    private int maxFailCount = 5;

    /** 统计连续失败次数的时间窗口（分钟）：超过这个时间没再输错，计数清零 */
    private int failWindowMinutes = 15;

    /** 锁定时长（分钟） */
    private int lockMinutes = 15;
}
