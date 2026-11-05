package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 每个人的一份责任书：岗位职责快照、手写签名、年度考核 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resp_sign")
public class RespSign extends BaseEntity {
    private Long bookId;
    private Long userId;
    private String userName;
    /** 发布时所在的岗位（名称快照） */
    private String posts;
    /** 发布时的岗位职责（JSON 数组快照，之后改岗位职责不影响已发布的责任书） */
    private String duties;
    /** PENDING / SIGNED */
    private String status;
    private LocalDateTime signTime;
    /** 手写签名 PNG（data URL），列表接口不返回 */
    @TableField(select = false)
    private String signImage;
    private String signHash;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer score;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String grade;
    private String assessComment;
    private String assessorName;
    private LocalDateTime assessTime;
    @Version
    private Integer version;

    /** 是否逾期签署，查询时计算 */
    @TableField(exist = false)
    private Boolean late;
}
