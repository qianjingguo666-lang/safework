package com.safework.system.message;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 通知公告（按企业隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {
    private String title;
    private String content;
    /** NORMAL 普通 / IMPORTANT 重要（重要公告前端会弹窗提醒） */
    private String level;
    private Long publisherId;
    private String publisherName;
    private Integer receiverCount;
}
