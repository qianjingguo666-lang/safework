package com.safework.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.tenant.TenantContext;
import com.safework.system.dto.MenuVO;
import com.safework.system.entity.SysMenu;
import com.safework.system.entity.SysRole;
import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysTenantPackage;
import com.safework.system.mapper.SysMenuMapper;
import com.safework.system.mapper.SysRoleMapper;
import com.safework.system.security.MenuIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计算用户的角色、权限码、菜单树。
 * <p>
 * 用户最终能用的菜单 = 角色分配的菜单 ∩ 企业套餐包含的菜单：
 * - 企业管理员：套餐内全部菜单（不再是 * 通配符，否则套餐限制不住管理员）；
 * - 运营平台（租户 0）：全部平台端菜单（scope = P）；
 * - 平台把企业从旗舰版降级为标准版后，角色里多出来的菜单自动失效，不需要去改每个角色。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final TenantCache tenantCache;

    public List<String> getRoleCodes(Long userId) {
        return roleMapper.selectRoleCodesByUserId(userId);
    }

    public boolean isAdmin(Long userId) {
        return getRoleCodes(userId).contains(SysRole.ADMIN_CODE);
    }

    /** 权限码列表 */
    public List<String> getPermissions(Long userId) {
        return allowedMenus(userId).stream()
                .map(SysMenu::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /** 当前用户可见的菜单树（只含目录和页面） */
    public List<MenuVO> getMenuTree(Long userId) {
        List<SysMenu> pages = allowedMenus(userId).stream()
                .filter(m -> !SysMenu.TYPE_BUTTON.equals(m.getMenuType()))
                .toList();
        return buildTree(pages);
    }

    /** 当前企业可分配的菜单树（套餐内，含按钮），给角色分配权限时使用 */
    public List<MenuVO> getAllMenuTree() {
        Set<Long> available = availableMenuIds();
        return buildTree(listAllMenus().stream().filter(m -> available.contains(m.getId())).toList());
    }

    /** 全部企业端菜单树，平台编辑套餐时勾选 */
    public List<MenuVO> getTenantScopeMenuTree() {
        return buildTree(listAllMenus().stream().filter(m -> SysMenu.SCOPE_TENANT.equals(m.getScope())).toList());
    }

    /** 全部企业端菜单 ID → 父 ID */
    public Map<Long, Long> tenantScopeParentMap() {
        return listAllMenus().stream()
                .filter(m -> SysMenu.SCOPE_TENANT.equals(m.getScope()))
                .collect(Collectors.toMap(SysMenu::getId, SysMenu::getParentId));
    }

    /**
     * 当前租户可以使用的菜单 ID。拿不到套餐时返回空集合（失败即拒绝）。
     */
    public Set<Long> availableMenuIds() {
        List<SysMenu> all = listAllMenus();
        if (TenantContext.isPlatform()) {
            return all.stream()
                    .filter(m -> SysMenu.SCOPE_PLATFORM.equals(m.getScope()))
                    .map(SysMenu::getId)
                    .collect(Collectors.toSet());
        }
        SysTenant tenant = tenantCache.getTenant(TenantContext.get());
        SysTenantPackage pkg = tenant == null ? null : tenantCache.getPackage(tenant.getPackageId());
        if (pkg == null || pkg.getStatus() == null || pkg.getStatus() != 1) {
            return Set.of();
        }
        return packageMenuIds(pkg.getMenuIds(), all);
    }

    /** 套餐里的菜单 ID：只保留企业端菜单，并且上级菜单也必须在套餐里 */
    public Set<Long> packageMenuIds(String menuIds, List<SysMenu> all) {
        Map<Long, Long> parentOf = all.stream()
                .filter(m -> SysMenu.SCOPE_TENANT.equals(m.getScope()))
                .collect(Collectors.toMap(SysMenu::getId, SysMenu::getParentId));
        return MenuIds.closedUnderParents(MenuIds.parse(menuIds), parentOf);
    }

    private List<SysMenu> allowedMenus(Long userId) {
        Set<Long> available = availableMenuIds();
        if (available.isEmpty()) {
            return List.of();
        }
        List<SysMenu> base = isAdmin(userId) ? listAllMenus() : menuMapper.selectMenusByUserId(userId);
        return base.stream().filter(m -> available.contains(m.getId())).toList();
    }

    public List<SysMenu> listAllMenus() {
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
