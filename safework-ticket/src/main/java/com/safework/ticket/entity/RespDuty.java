package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位安全职责（岗位复用劳保模块的 ppe_post），一个岗位一份 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resp_duty")
public class RespDuty extends BaseEntity {
    private Long postId;
    /** 职责清单（JSON 字符串数组） */
    private String duties;
    private String updaterName;
}
