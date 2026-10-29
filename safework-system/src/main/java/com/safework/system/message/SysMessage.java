package com.safework.system.message;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内消息：每个接收人一条（tenant_id 由租户插件自动写入） */
@Data
@TableName("sys_message")
public class SysMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    /** 见 {@link MessageCategory} */
    private String category;
    private String bizType;
    private Long bizId;
    private String title;
    private String content;
    private String link;
    private String senderName;
    /** 0 未读 1 已读 */
    private Integer readFlag;
    private LocalDateTime readTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
