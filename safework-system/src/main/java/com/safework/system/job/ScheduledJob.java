package com.safework.system.job;

/**
 * 定时任务。实现这个接口并注册成 Spring Bean，{@link JobRunner} 会自动按 cron 调度、加分布式锁、记录执行日志，
 * 平台的【定时任务】页面也会自动出现这个任务，可以手动执行。
 * <p>
 * 注意：定时任务没有 Web 请求，TenantContext 是空的 —— 直接查业务表会被租户插件拒绝（失败即拒绝）。
 * 需要处理企业数据时，用 {@link TenantJobHelper#forEachActiveTenant} 逐个企业切换租户执行。
 */
public interface ScheduledJob {

    /** 任务编码，如 hazard-deadline */
    String code();

    String name();

    /** Spring cron 表达式（6 位：秒 分 时 日 月 周），如 0 30 8 * * * 表示每天 8:30 */
    String cron();

    String description();

    /** 执行任务，返回结果摘要（会记录到执行日志）；抛异常表示执行失败 */
    String execute();
}
