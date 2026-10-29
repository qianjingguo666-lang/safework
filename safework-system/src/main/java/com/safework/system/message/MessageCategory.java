package com.safework.system.message;

/** 消息分类 */
public final class MessageCategory {
    /** 待办：需要我去处理（审批作业票、整改隐患、验收） */
    public static final String TODO = "TODO";
    /** 通知：告诉我结果（作业票已批准、隐患已闭环） */
    public static final String NOTICE = "NOTICE";
    /** 预警：定时任务发现的风险（隐患逾期、作业超时、服务即将到期） */
    public static final String WARN = "WARN";
    /** 企业公告 */
    public static final String ANNOUNCE = "ANNOUNCE";

    private MessageCategory() {
    }
}
