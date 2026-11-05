package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 发放记录：重新发放同一种用品时，旧记录自动变成“已更换” */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ppe_issue")
public class PpeIssue extends BaseEntity {
    private Long userId;
    private String userName;
    private Long itemId;
    private String itemName;
    private Integer quantity;
    private LocalDate issueDate;
    private LocalDate expireDate;
    /** IN_USE / REPLACED */
    private String status;
    private String issuerName;
    private String remark;
}
