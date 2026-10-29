package com.safework.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.system.dto.MenuVO;
import com.safework.system.dto.platform.PackageSaveDTO;
import com.safework.system.dto.platform.PackageVO;
import com.safework.system.entity.SysMenu;
import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysTenantPackage;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.mapper.SysTenantPackageMapper;
import com.safework.system.security.MenuIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 套餐管理（平台端）
 */
@Service
@RequiredArgsConstructor
public class TenantPackageService {

    private final SysTenantPackageMapper packageMapper;
    private final SysTenantMapper tenantMapper;
    private final PermissionService permissionService;
    private final TenantCache tenantCache;

    public List<PackageVO> list() {
        Map<Long, Long> tenantCount = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                        .select(SysTenant::getPackageId)
                        .isNotNull(SysTenant::getPackageId))
                .stream().collect(Collectors.groupingBy(SysTenant::getPackageId, Collectors.counting()));
        Map<Long, SysMenu> menus = permissionService.listAllMenus().stream()
                .collect(Collectors.toMap(SysMenu::getId, m -> m));
        return packageMapper.selectList(Wrappers.<SysTenantPackage>lambdaQuery()
                        .orderByAsc(SysTenantPackage::getSort, SysTenantPackage::getId))
                .stream().map(p -> {
                    PackageVO vo = new PackageVO();
                    vo.setId(p.getId());
                    vo.setPackageName(p.getPackageName());
                    Set<Long> ids = MenuIds.parse(p.getMenuIds());
                    vo.setMenuIds(ids.stream().sorted().toList());
                    vo.setPageCount((int) ids.stream().map(menus::get)
                            .filter(m -> m != null && SysMenu.TYPE_PAGE.equals(m.getMenuType())).count());
                    vo.setSort(p.getSort());
                    vo.setStatus(p.getStatus());
                    vo.setRemark(p.getRemark());
                    vo.setTenantCount(tenantCount.getOrDefault(p.getId(), 0L));
                    vo.setCreateTime(p.getCreateTime());
                    return vo;
                }).toList();
    }

    /** 下拉框：只返回正常状态的套餐 */
    public List<SysTenantPackage> options() {
        return packageMapper.selectList(Wrappers.<SysTenantPackage>lambdaQuery()
                .select(SysTenantPackage::getId, SysTenantPackage::getPackageName)
                .eq(SysTenantPackage::getStatus, 1)
                .orderByAsc(SysTenantPackage::getSort));
    }

    public SysTenantPackage getEnabled(Long id) {
        SysTenantPackage p = packageMapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "套餐不存在");
        }
        if (p.getStatus() == null || p.getStatus() != 1) {
            throw new BizException("套餐已停用，不能使用");
        }
        return p;
    }

    public List<MenuVO> menuTree() {
        return permissionService.getTenantScopeMenuTree();
    }

    public Long create(PackageSaveDTO dto) {
        SysTenantPackage p = new SysTenantPackage();
        fill(p, dto);
        p.setStatus(1);
        packageMapper.insert(p);
        return p.getId();
    }

    /** 修改套餐：使用这个套餐的所有企业立即按新的功能范围生效（权限是实时计算的） */
    public void update(Long id, PackageSaveDTO dto) {
        SysTenantPackage p = get(id);
        fill(p, dto);
        packageMapper.updateById(p);
        tenantCache.evictPackage(id);
    }

    public void changeStatus(Long id, Integer status) {
        SysTenantPackage p = get(id);
        if (status == 0 && countTenants(id) > 0) {
            throw new BizException("还有企业在使用这个套餐，停用后这些企业将无法使用任何功能，请先为它们更换套餐");
        }
        p.setStatus(status);
        packageMapper.updateById(p);
        tenantCache.evictPackage(id);
    }

    public void remove(Long id) {
        get(id);
        if (countTenants(id) > 0) {
            throw new BizException("还有企业在使用这个套餐，不能删除");
        }
        packageMapper.deleteById(id);
        tenantCache.evictPackage(id);
    }

    private SysTenantPackage get(Long id) {
        SysTenantPackage p = packageMapper.selectById(id);
        if (p == null) {
            throw new BizException(404, "套餐不存在");
        }
        return p;
    }

    private long countTenants(Long packageId) {
        return tenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getPackageId, packageId));
    }

    private void fill(SysTenantPackage p, PackageSaveDTO dto) {
        Set<Long> ids = new HashSet<>(dto.getMenuIds());
        Map<Long, Long> parentOf = permissionService.tenantScopeParentMap();
        // 只能放企业端菜单：平台菜单（企业管理、套餐管理）绝不能进套餐
        if (!parentOf.keySet().containsAll(ids)) {
            throw new BizException("包含不存在的菜单或平台专用菜单");
        }
        Set<Long> closed = MenuIds.closedUnderParents(ids, parentOf);
        if (closed.size() != ids.size()) {
            throw new BizException("勾选了按钮或页面，但没有勾选它的上级菜单");
        }
        if (closed.isEmpty()) {
            throw new BizException("请至少选择一个功能");
        }
        p.setPackageName(dto.getPackageName().trim());
        p.setMenuIds(MenuIds.join(closed));
        p.setSort(dto.getSort() == null ? 0 : dto.getSort());
        p.setRemark(dto.getRemark());
    }
}
