package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 自评里的一个考评项目（建自评时从 StdRules.ITEMS 复制快照，以后改标准不影响已有自评） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("std_item")
public class StdItem extends BaseEntity {
    private Long assessmentId;
    private String code;
    private Integer element;
    private String elementName;
    private String title;
    private String content;
    private Integer standardScore;
    private String method;
    /** 1 系统能自动取证 */
    private Integer auto;
    /** 1 空项（不参与计分） */
    private Integer na;
    private Double deduct;
    /** 系统建议扣分 */
    private Double suggested;
    /** 证据：JSON 字符串数组 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String evidence;
    /** 扣分说明 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String note;
    private Long hazardId;
    private String hazardNo;
    private Integer sort;
}
