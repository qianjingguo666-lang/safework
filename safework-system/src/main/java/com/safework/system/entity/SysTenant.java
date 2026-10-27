package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户（一家企业 = 一个租户）。这张表本身不做租户隔离。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
public class SysTenant extends BaseEntity {
    /** 租户编码，登录时填写，如 demo */
    private String tenantCode;
    private String tenantName;
    private String contactName;
    private String contactPhone;
    /** 1 正常 0 停用 */
    private Integer status;
    /** 到期时间，为空表示不限 */
    private LocalDateTime expireTime;
}
