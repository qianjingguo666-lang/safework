package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户套餐（平台级，不做租户隔离）。menuIds 是逗号分隔的菜单 ID，企业只能使用套餐内的菜单和按钮。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant_package")
public class SysTenantPackage extends BaseEntity {
    private String packageName;
    private String menuIds;
    private Integer sort;
    /** 1 正常 0 停用（停用后使用该套餐的企业所有功能不可用） */
    private Integer status;
    private String remark;
}
