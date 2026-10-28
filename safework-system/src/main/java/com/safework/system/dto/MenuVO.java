package com.safework.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 菜单树节点 */
@Data
public class MenuVO {
    private Long id;
    private Long parentId;
    private String menuName;
    private String menuType;
    private String path;
    private String component;
    private String perms;
    private String icon;
    private Integer sort;
    private List<MenuVO> children = new ArrayList<>();
}
