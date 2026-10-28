package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色（按租户隔离）。role_code = admin 的是企业管理员，拥有全部权限。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    public static final String ADMIN_CODE = "admin";

    private String roleCode;
    private String roleName;
    private Integer sort;
    /** 1 正常 0 停用 */
    private Integer status;
    private String remark;
}
