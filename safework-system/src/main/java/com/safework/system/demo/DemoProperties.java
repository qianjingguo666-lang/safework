package com.safework.system.demo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 演示模式（application.yml 里 safework.demo）：
 * 把地址公开给别人自测时打开。演示企业的账号谁都能登录，所以要限制危险操作、限制 AI 用量，数据每天凌晨自动恢复。
 */
@Data
@ConfigurationProperties(prefix = "safework.demo")
public class DemoProperties {

    /** 是否开启演示模式（生产环境用环境变量 DEMO_MODE=true 打开） */
    private boolean enabled = false;
    /** 哪些企业是演示企业：这些企业的账号受限制；平台管理员和其他企业不受影响 */
    private List<String> tenantCodes = new ArrayList<>(List.of("demo", "test"));
    /** 每个演示企业每天最多调用 AI 多少次（问答、智能体、预审、出题、语音上报都算），0 表示不限 */
    private int aiDailyLimit = 200;
    /** 每天几点恢复数据（只用于页面提示，真正的恢复由服务器上的定时任务 demo-reset.sh 执行） */
    private String resetTime = "04:00";
    /** 登录页是否列出演示账号：演示模式打开时总是列出；开发环境也打开，方便切换账号 */
    private boolean showAccounts = false;
    /** 演示账号的密码（只用于登录页展示） */
    private String password = "admin123";
    /** 登录页列出的演示账号 */
    private List<Account> accounts = new ArrayList<>();

    @Data
    public static class Account {
        private String label;
        private String tenantCode;
        private String username;
        /** 一句话说明这个角色能做什么 */
        private String tip;
    }
}
