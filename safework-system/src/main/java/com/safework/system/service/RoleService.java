package com.safework.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.system.dto.RoleCreateDTO;
import com.safework.system.entity.SysRole;
import com.safework.system.entity.SysRoleMenu;
import com.safework.system.mapper.SysMenuMapper;
import com.safework.system.mapper.SysRoleMapper;
import com.safework.system.mapper.SysRoleMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final PermissionService permissionService;

    public List<SysRole> list() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getSort));
    }

    public SysRole getById(Long id) {
        // selectById 也会带上 tenant_id 条件，所以查不到别的企业的角色
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        return role;
    }

    public Long create(RoleCreateDTO dto) {
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, dto.getRoleCode()));
        if (count > 0) {
            throw new BizException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setSort(dto.getSort() == null ? 99 : dto.getSort());
        role.setRemark(dto.getRemark());
        role.setStatus(1);
        roleMapper.insert(role);
        return role.getId();
    }

    public List<Long> getMenuIds(Long roleId) {
        getById(roleId);
        return roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    /** 给角色分配菜单：先删后插，放在一个事务里 */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        SysRole role = getById(roleId);
        if (SysRole.ADMIN_CODE.equals(role.getRoleCode())) {
            throw new BizException("企业管理员默认拥有套餐内全部权限，无需分配");
        }
        Set<Long> ids = new HashSet<>(menuIds);
        if (!ids.isEmpty() && menuMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new BizException("包含不存在的菜单");
        }
        // 只能分配套餐内的菜单：防止绕过前端，直接调接口把平台菜单或更高级套餐的功能分配给角色
        if (!permissionService.availableMenuIds().containsAll(ids)) {
            throw new BizException("包含当前套餐未开通的功能");
        }
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        for (Long menuId : ids) {
            roleMenuMapper.insert(new SysRoleMenu(roleId, menuId));
        }
    }

    /** 校验角色 ID 都属于当前企业，返回去重后的集合 */
    public Set<Long> checkRoleIds(List<Long> roleIds) {
        Set<Long> ids = new HashSet<>(roleIds);
        if (!ids.isEmpty() && roleMapper.selectBatchIds(ids).size() != ids.size()) {
            throw new BizException("角色不存在");
        }
        return ids;
    }
}
