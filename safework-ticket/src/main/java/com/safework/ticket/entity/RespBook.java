package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 年度安全生产责任书：通用条款 + 各人岗位职责，发布后每人一份待签署 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resp_book")
public class RespBook extends BaseEntity {
    private Integer year;
    private String title;
    /** 通用条款（所有人都一样的部分） */
    private String content;
    private LocalDate deadline;
    /** DRAFT / PUBLISHED / CLOSED */
    private String status;
    private LocalDateTime publishTime;
    private String publisherName;
    private String remark;
}
