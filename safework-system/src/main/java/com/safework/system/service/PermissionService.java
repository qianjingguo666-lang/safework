package com.safework.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.system.dto.MenuVO;
import com.safework.system.entity.SysMenu;
import com.safework.system.entity.SysRole;
import com.safework.system.mapper.SysMenuMapper;
import com.safework.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 计算用户的角色、权限码、菜单树
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** Sa-Token 的通配符，表示拥有全部权限 */
    public static final String ALL_PERMISSION = "*";

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    public List<String> getRoleCodes(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    public boolean isAdmin(Long userId) {
        return getRoleCodes(userId).contains(SysRole.ADMIN_CODE);
    }

    /** 权限码列表；企业管理员返回 ["*"] */
    public List<String> getPermissions(Long userId) {
        if (isAdmin(userId)) {
            return List.of(ALL_PERMISSION);
        }
        return getUserMenus(userId).stream()
                .map(SysMenu::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /** 当前用户可见的菜单树（只含目录和页面） */
    public List<MenuVO> getMenuTree(Long userId) {
        List<SysMenu> menus = isAdmin(userId) ? listAllMenus() : getUserMenus(userId);
        List<SysMenu> pages = menus.stream()
                .filter(m -> !SysMenu.TYPE_BUTTON.equals(m.getMenuType()))
                .toList();
        return buildTree(pages);
    }

    /** 全部菜单（含按钮），给角色分配权限时使用 */
    public List<MenuVO> getAllMenuTree() {
        return buildTree(listAllMenus());
    }

    private List<SysMenu> getUserMenus(Long userId) {
        return menuMapper.selectMenusByUserId(userId);
    }

    private List<SysMenu> listAllMenus() {
        return menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getStatus, 1)
                .orderByAsc(SysMenu::getSort));
    }

    /** 扁平列表转树。找不到父节点的节点作为根节点，避免权限配置不完整时菜单直接消失 */
    private List<MenuVO> buildTree(List<SysMenu> menus) {
        Map<Long, MenuVO> map = new LinkedHashMap<>();
        for (SysMenu m : menus) {
            MenuVO vo = new MenuVO();
            vo.setId(m.getId());
            vo.setParentId(m.getParentId());
            vo.setMenuName(m.getMenuName());
            vo.setMenuType(m.getMenuType());
            vo.setPath(m.getPath());
            vo.setComponent(m.getComponent());
            vo.setPerms(m.getPerms());
            vo.setIcon(m.getIcon());
            vo.setSort(m.getSort());
            map.put(vo.getId(), vo);
        }
        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO vo : map.values()) {
            MenuVO parent = map.get(vo.getParentId());
            if (parent != null) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuVO> nodes) {
        nodes.sort(Comparator.comparing(MenuVO::getSort, Comparator.nullsLast(Integer::compareTo)));
        nodes.stream().map(MenuVO::getChildren).filter(Objects::nonNull).forEach(this::sortTree);
    }
}
