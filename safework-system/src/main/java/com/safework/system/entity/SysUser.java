package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 用户。表里有 tenant_id 字段，但实体不写，查询/新增时由租户插件自动处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {
    private String username;
    /** BCrypt 密文，不返回给前端 */
    @JsonIgnore
    private String password;
    private String nickname;
    private String phone;
    private Long deptId;
    /** 1 正常 0 停用 */
    private Integer status;

    /** 以下两个字段不在 sys_user 表里，查询列表时填充，方便前端显示 */
    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private List<String> roleNames;
}
