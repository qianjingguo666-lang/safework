package com.safework.system.dto;

import com.safework.system.entity.SysUser;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 当前登录用户信息：前端据此生成菜单、控制按钮显示 */
@Data
@AllArgsConstructor
public class UserInfoVO {
    private SysUser user;
    private List<String> roles;
    private List<String> permissions;
    /** 只包含目录和页面，不含按钮 */
    private List<MenuVO> menus;
}
