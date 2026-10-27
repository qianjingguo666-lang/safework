package com.safework.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
    /** 1 正常 0 停用 */
    private Integer status;
}
