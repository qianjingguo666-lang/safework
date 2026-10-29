package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单（平台级，所有租户共用同一套菜单，不做租户隔离）。企业能用哪些由套餐决定。
 * menu_type：M 目录，C 页面，F 按钮
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    public static final String TYPE_DIR = "M";
    public static final String TYPE_PAGE = "C";
    public static final String TYPE_BUTTON = "F";

    /** 企业端菜单：可以放进套餐，企业按套餐使用 */
    public static final String SCOPE_TENANT = "T";
    /** 平台端菜单：只有运营平台能看到（企业管理、套餐管理） */
    public static final String SCOPE_PLATFORM = "P";

    private Long parentId;
    private String menuName;
    private String menuType;
    private String scope;
    /** 前端路由地址 */
    private String path;
    /** 前端组件路径 */
    private String component;
    /** 权限码，如 system:user:add */
    private String perms;
    private String icon;
    private Integer sort;
    private Integer status;
}
