package com.safework.system.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.dto.TenantInfoVO;
import com.safework.system.dto.platform.PlatformStatsVO;
import com.safework.system.dto.platform.TenantCreateDTO;
import com.safework.system.dto.platform.TenantQueryDTO;
import com.safework.system.dto.platform.TenantUpdateDTO;
import com.safework.system.dto.platform.TenantVO;
import com.safework.system.entity.SysDept;
import com.safework.system.entity.SysRole;
import com.safework.system.entity.SysRoleMenu;
import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysTenantPackage;
import com.safework.system.entity.SysUser;
import com.safework.system.entity.SysUserRole;
import com.safework.system.mapper.SysDeptMapper;
import com.safework.system.mapper.SysRoleMapper;
import com.safework.system.mapper.SysRoleMenuMapper;
import com.safework.system.mapper.SysTenantMapper;
import com.safework.system.mapper.SysTenantPackageMapper;
import com.safework.system.mapper.SysUserMapper;
import com.safework.system.mapper.SysUserRoleMapper;
import com.safework.system.security.LoginGuard;
import com.safework.system.security.OnlineUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 企业（租户）管理：平台端使用。
 * 这里的大部分方法要操作“别的企业”的数据，通过 TenantContext.runAs 临时切换租户，
 * 而不是关掉租户插件 —— 这样即使写错了，也只会影响被切换到的那一个企业。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    /** 保留的企业编码，不能被开通 */
    private static final Set<String> RESERVED_CODES = Set.of("platform", "admin", "system", "root", "api", "www");
    private static final int EXPIRING_DAYS = 30;

    private final SysTenantMapper tenantMapper;
    private final SysTenantPackageMapper packageMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysDeptMapper deptMapper;
    private final TenantPackageService packageService;
    private final PermissionService permissionService;
    private final OnlineUserService onlineUserService;
    private final LoginGuard loginGuard;
    private final LoginLogService loginLogService;
    private final TenantCache tenantCache;

    // ======================== 查询 ========================

    public IPage<TenantVO> page(TenantQueryDTO q) {
        LocalDateTime soon = LocalDateTime.now().plusDays(EXPIRING_DAYS);
        LambdaQueryWrapper<SysTenant> w = Wrappers.<SysTenant>lambdaQuery()
                .ne(SysTenant::getId, TenantContext.PLATFORM_ID)
                .and(StringUtils.hasText(q.getKeyword()), x -> x
                        .like(SysTenant::getTenantCode, q.getKeyword())
                        .or()
                        .like(SysTenant::getTenantName, q.getKeyword()))
                .eq(q.getStatus() != null, SysTenant::getStatus, q.getStatus())
                .eq(q.getPackageId() != null, SysTenant::getPackageId, q.getPackageId())
                .le(Boolean.TRUE.equals(q.getExpiring()), SysTenant::getExpireTime, soon)
                .orderByDesc(SysTenant::getCreateTime);
        Page<SysTenant> page = tenantMapper.selectPage(new Page<>(q.getCurrent(), q.getSize()), w);
        Map<Long, Long> userCounts = userCounts();
        Map<Long, String> packageNames = packageNames();
        return page.convert(t -> toVO(t, userCounts, packageNames, true));
    }

    public TenantVO detail(Long id) {
        return toVO(get(id), userCounts(), packageNames(), true);
    }

    /** 当前登录用户所属企业（显示在页面顶部） */
    public TenantInfoVO currentInfo() {
        SysTenant t = tenantCache.getTenant(TenantContext.get());
        TenantInfoVO vo = new TenantInfoVO();
        if (t == null) {
            return vo;
        }
        vo.setTenantId(t.getId());
        vo.setTenantCode(t.getTenantCode());
        vo.setTenantName(t.getTenantName());
        vo.setPlatform(TenantContext.PLATFORM_ID.equals(t.getId()));
        SysTenantPackage p = tenantCache.getPackage(t.getPackageId());
        vo.setPackageName(p == null ? null : p.getPackageName());
        vo.setExpireTime(t.getExpireTime());
        vo.setDaysLeft(daysLeft(t.getExpireTime()));
        vo.setAccountLimit(t.getAccountLimit());
        return vo;
    }

    public PlatformStatsVO stats() {
        List<SysTenant> tenants = tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery()
                .ne(SysTenant::getId, TenantContext.PLATFORM_ID));
        Map<Long, Long> userCounts = userCounts();
        Map<Long, String> packageNames = packageNames();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime soon = now.plusDays(EXPIRING_DAYS);

        PlatformStatsVO vo = new PlatformStatsVO();
        vo.setTenantTotal(tenants.size());
        vo.setTenantEnabled(tenants.stream().filter(t -> Integer.valueOf(1).equals(t.getStatus())).count());
        vo.setTenantExpired(tenants.stream().filter(SysTenant::expired).count());
        vo.setTenantExpiringSoon(tenants.stream().filter(t -> !t.expired() && t.getExpireTime() != null
                && t.getExpireTime().isBefore(soon)).count());
        vo.setUserTotal(tenants.stream().mapToLong(t -> userCounts.getOrDefault(t.getId(), 0L)).sum());
        vo.setOnlineTotal(tenants.stream().mapToInt(t -> onlineUserService.count(t.getId())).sum());
        vo.setLoginSuccessToday(loginLogService.countTodayAllTenants(1));
        vo.setLoginFailToday(loginLogService.countTodayAllTenants(0));
        vo.setExpiringTenants(tenants.stream()
                .filter(t -> t.getExpireTime() != null && t.getExpireTime().isBefore(soon))
                .sorted(Comparator.comparing(SysTenant::getExpireTime))
                .limit(10)
                .map(t -> toVO(t, userCounts, packageNames, false))
                .toList());
        return vo;
    }

    // ======================== 开通企业 ========================

    /**
     * 开通企业，一个事务里完成：
     * 1. 新建企业；2. 切换到新企业，创建根部门、企业管理员角色和预置角色（按套餐分配菜单）；3. 创建管理员账号。
     * 任何一步失败全部回滚，不会出现“企业建好了但没有管理员、谁都登不进去”的情况。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(TenantCreateDTO dto) {
        String code = dto.getTenantCode().trim();
        if (RESERVED_CODES.contains(code)) {
            throw new BizException("企业编码 " + code + " 是系统保留字，请换一个");
        }
        if (tenantMapper.selectCount(Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getTenantCode, code)) > 0) {
            throw new BizException("企业编码已存在");
        }
        SysTenantPackage pkg = packageService.getEnabled(dto.getPackageId());
        checkExpireTime(dto.getExpireTime());

        SysTenant tenant = new SysTenant();
        tenant.setTenantCode(code);
        tenant.setTenantName(dto.getTenantName().trim());
        tenant.setContactName(dto.getContactName());
        tenant.setContactPhone(dto.getContactPhone());
        tenant.setPackageId(pkg.getId());
        tenant.setAccountLimit(dto.getAccountLimit());
        tenant.setExpireTime(dto.getExpireTime());
        tenant.setRemark(dto.getRemark());
        tenant.setStatus(1);
        tenantMapper.insert(tenant);

        Set<Long> packageMenus = permissionService.packageMenuIds(pkg.getMenuIds(), permissionService.listAllMenus());
        TenantContext.runAs(tenant.getId(), () -> initTenantData(tenant, packageMenus, dto));
        log.info("开通企业 {}（{}），套餐 {}", tenant.getTenantName(), code, pkg.getPackageName());
        return tenant.getId();
    }

    /** 在新企业的租户上下文里执行：tenant_id 由租户插件自动写入新企业 ID */
    private void initTenantData(SysTenant tenant, Set<Long> packageMenus, TenantCreateDTO dto) {
        SysDept root = new SysDept();
        root.setParentId(0L);
        root.setDeptName(tenant.getTenantName());
        root.setLeader(dto.getContactName());
        root.setPhone(dto.getContactPhone());
        root.setSort(0);
        root.setStatus(1);
        deptMapper.insert(root);

        SysRole admin = newRole(SysRole.ADMIN_CODE, "企业管理员", 1, "拥有套餐内全部权限");
        roleMapper.insert(admin);
        for (PresetRoles.Preset p : PresetRoles.ALL) {
            SysRole role = newRole(p.code(), p.name(), p.sort(), p.remark());
            roleMapper.insert(role);
            p.menuIds().stream()
                    .filter(packageMenus::contains)
                    .forEach(menuId -> roleMenuMapper.insert(new SysRoleMenu(role.getId(), menuId)));
        }

        SysUser user = new SysUser();
        user.setUsername(dto.getAdminUsername().trim());
        user.setPassword(BCrypt.hashpw(dto.getAdminPassword()));
        user.setNickname(tenant.getTenantName() + "管理员");
        user.setPhone(dto.getContactPhone());
        user.setDeptId(root.getId());
        user.setStatus(1);
        userMapper.insert(user);
        userRoleMapper.insert(new SysUserRole(user.getId(), admin.getId()));
    }

    private static SysRole newRole(String code, String name, int sort, String remark) {
        SysRole r = new SysRole();
        r.setRoleCode(code);
        r.setRoleName(name);
        r.setSort(sort);
        r.setRemark(remark);
        r.setStatus(1);
        return r;
    }

    // ======================== 修改、停用、续费 ========================

    public void update(Long id, TenantUpdateDTO dto) {
        SysTenant t = get(id);
        if (!dto.getPackageId().equals(t.getPackageId())) {
            packageService.getEnabled(dto.getPackageId());
        }
        if (dto.getAccountLimit() != null) {
            long users = userCounts().getOrDefault(id, 0L);
            if (dto.getAccountLimit() < users) {
                throw new BizException("账号上限不能小于企业现有账号数（" + users + " 个）");
            }
        }
        t.setTenantName(dto.getTenantName().trim());
        t.setContactName(dto.getContactName());
        t.setContactPhone(dto.getContactPhone());
        t.setPackageId(dto.getPackageId());
        t.setRemark(dto.getRemark());
        // updateById 默认跳过 null 字段，“改成不限期 / 不限账号”需要显式写 NULL，所以这两个字段放在 set 里
        t.setExpireTime(null);
        t.setAccountLimit(null);
        tenantMapper.update(t, Wrappers.<SysTenant>lambdaUpdate()
                .eq(SysTenant::getId, id)
                .set(SysTenant::getExpireTime, dto.getExpireTime())
                .set(SysTenant::getAccountLimit, dto.getAccountLimit()));
        tenantCache.evictTenant(id);
    }

    /** 停用企业：该企业所有在线用户立即被踢下线，之后无法登录 */
    public void changeStatus(Long id, Integer status) {
        SysTenant t = get(id);
        t.setStatus(status);
        tenantMapper.updateById(t);
        tenantCache.evictTenant(id);
        if (status == 0) {
            kickoutAll(id);
        }
    }

    /** 重置企业管理员密码（客户忘记密码时平台帮忙重置），同时解除锁定 */
    public String resetAdminPassword(Long id, String password) {
        get(id);
        return TenantContext.callAs(id, () -> {
            List<SysUser> admins = userMapper.selectByRoleCode(SysRole.ADMIN_CODE);
            if (admins.isEmpty()) {
                throw new BizException("该企业没有正常状态的管理员账号");
            }
            SysUser admin = admins.get(0);
            admin.setPassword(BCrypt.hashpw(password));
            userMapper.updateById(admin);
            StpUtil.logout(admin.getId());
            loginGuard.unlock(id, admin.getUsername());
            return admin.getUsername();
        });
    }

    private void kickoutAll(Long tenantId) {
        List<SysUser> users = TenantContext.callAs(tenantId, () -> userMapper.selectList(
                Wrappers.<SysUser>lambdaQuery().select(SysUser::getId)));
        users.forEach(u -> StpUtil.logout(u.getId()));
        onlineUserService.clear(tenantId);
    }

    // ======================== 工具方法 ========================

    /** 查企业（不能操作平台自己） */
    private SysTenant get(Long id) {
        if (TenantContext.PLATFORM_ID.equals(id)) {
            throw new BizException("不能修改运营平台自身");
        }
        SysTenant t = tenantMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "企业不存在");
        }
        return t;
    }

    private static void checkExpireTime(LocalDateTime expireTime) {
        if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
            throw new BizException("到期时间不能早于现在");
        }
    }

    private Map<Long, Long> userCounts() {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : userMapper.countGroupByTenant()) {
            Object tid = row.get("tenantId");
            Object cnt = row.get("cnt");
            if (tid != null && cnt != null) {
                result.put(((Number) tid).longValue(), ((Number) cnt).longValue());
            }
        }
        return result;
    }

    private Map<Long, String> packageNames() {
        return packageMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysTenantPackage::getId, SysTenantPackage::getPackageName, (a, b) -> a));
    }

    static Long daysLeft(LocalDateTime expireTime) {
        if (expireTime == null) {
            return null;
        }
        return Duration.between(LocalDate.now().atStartOfDay(), expireTime.toLocalDate().atStartOfDay()).toDays();
    }

    private TenantVO toVO(SysTenant t, Map<Long, Long> userCounts, Map<Long, String> packageNames, boolean withOnline) {
        TenantVO vo = new TenantVO();
        vo.setId(t.getId());
        vo.setTenantCode(t.getTenantCode());
        vo.setTenantName(t.getTenantName());
        vo.setContactName(t.getContactName());
        vo.setContactPhone(t.getContactPhone());
        vo.setPackageId(t.getPackageId());
        vo.setPackageName(packageNames.get(t.getPackageId()));
        vo.setAccountLimit(t.getAccountLimit());
        vo.setUserCount(userCounts.getOrDefault(t.getId(), 0L));
        if (withOnline) {
            vo.setOnlineCount(onlineUserService.count(t.getId()));
        }
        vo.setStatus(t.getStatus());
        vo.setExpireTime(t.getExpireTime());
        vo.setDaysLeft(daysLeft(t.getExpireTime()));
        vo.setRemark(t.getRemark());
        vo.setCreateTime(t.getCreateTime());
        return vo;
    }
}
