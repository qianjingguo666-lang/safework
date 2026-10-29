package com.safework.system.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.dto.UserCreateDTO;
import com.safework.system.dto.UserOptionVO;
import com.safework.system.dto.UserUpdateDTO;
import com.safework.system.entity.SysRole;
import com.safework.system.entity.SysTenant;
import com.safework.system.entity.SysUser;
import com.safework.system.entity.SysUserRole;
import com.safework.system.mapper.SysRoleMapper;
import com.safework.system.mapper.SysUserMapper;
import com.safework.system.mapper.SysUserRoleMapper;
import com.safework.system.security.LoginGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final RoleService roleService;
    private final DeptService deptService;
    private final TenantCache tenantCache;
    private final LoginGuard loginGuard;

    /**
     * 分页查询：SQL 里不用写 tenant_id，插件会自动加上。
     * 按部门筛选时包含所有下级部门；查询后批量填充部门名称、角色名称（避免 N+1 查询）
     */
    public Page<SysUser> page(long current, long size, String keyword, Long deptId, Integer status) {
        Set<Long> deptIds = deptId == null ? null : deptService.selfAndDescendants(deptId);
        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size), Wrappers.<SysUser>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getNickname, keyword))
                .in(deptIds != null, SysUser::getDeptId, deptIds)
                .eq(status != null, SysUser::getStatus, status)
                .orderByAsc(SysUser::getId));
        fillNames(page.getRecords());
        return page;
    }

    private void fillNames(List<SysUser> users) {
        if (users.isEmpty()) {
            return;
        }
        Map<Long, String> deptNames = deptService.nameMap();
        List<Long> userIds = users.stream().map(SysUser::getId).toList();
        List<SysUserRole> links = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .in(SysUserRole::getUserId, userIds));
        Map<Long, String> roleNames = links.isEmpty() ? Map.of() : roleMapper.selectBatchIds(
                        links.stream().map(SysUserRole::getRoleId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName));
        Map<Long, List<String>> userRoles = links.stream().collect(Collectors.groupingBy(SysUserRole::getUserId,
                Collectors.mapping(l -> roleNames.getOrDefault(l.getRoleId(), "?"), Collectors.toList())));
        Long tenantId = TenantContext.get();
        for (SysUser u : users) {
            u.setDeptName(deptNames.get(u.getDeptId()));
            u.setRoleNames(userRoles.getOrDefault(u.getId(), new ArrayList<>()));
            u.setLocked(loginGuard.lockedSeconds(tenantId, u.getUsername()) > 0);
        }
    }

    /** 下拉选择（如隐患整改人）：所有登录用户都能调用，只返回 ID、昵称、部门 */
    public List<UserOptionVO> options() {
        Map<Long, String> deptNames = deptService.nameMap();
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId))
                .stream()
                .map(u -> new UserOptionVO(u.getId(), u.getNickname(), deptNames.get(u.getDeptId())))
                .toList();
    }

    public List<SysUser> listByRoleCode(String roleCode) {
        return userMapper.selectByRoleCode(roleCode);
    }

    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    /** 新增用户：tenant_id 由插件自动写入当前租户 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateDTO dto) {
        checkAccountLimit();
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (count > 0) {
            throw new BizException("用户名已存在");
        }
        if (dto.getDeptId() != null) {
            deptService.getById(dto.getDeptId());
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname()) ? dto.getNickname() : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setDeptId(dto.getDeptId());
        user.setStatus(1);
        userMapper.insert(user);
        if (dto.getRoleIds() != null && !dto.getRoleIds().isEmpty()) {
            assignRoles(user.getId(), dto.getRoleIds());
        }
        return user.getId();
    }

    public void update(Long id, UserUpdateDTO dto) {
        SysUser user = getById(id);
        if (dto.getDeptId() != null) {
            deptService.getById(dto.getDeptId());
        }
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setDeptId(dto.getDeptId());
        userMapper.updateById(user);
    }

    /** 停用 / 启用。停用时立即踢下线，已登录的会话马上失效 */
    public void changeStatus(Long id, Integer status) {
        SysUser user = getById(id);
        if (status == 0 && id.equals(StpUtil.getLoginIdAsLong())) {
            throw new BizException("不能停用自己的账号");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == 0) {
            StpUtil.logout(id);
        }
    }

    /** 套餐账号上限：由平台在开通企业时设置 */
    private void checkAccountLimit() {
        SysTenant tenant = tenantCache.getTenant(TenantContext.get());
        if (tenant == null || tenant.getAccountLimit() == null) {
            return;
        }
        long total = userMapper.selectCount(null);
        if (total >= tenant.getAccountLimit()) {
            throw new BizException("账号数量已达上限（" + tenant.getAccountLimit() + " 个），请联系平台扩容");
        }
    }

    /** 解除“密码错误次数过多”的锁定 */
    public void unlock(Long id) {
        SysUser user = getById(id);
        loginGuard.unlock(TenantContext.get(), user.getUsername());
    }

    /** 管理员重置密码：重置后该用户需要重新登录 */
    public void resetPassword(Long id, String password) {
        SysUser user = getById(id);
        user.setPassword(BCrypt.hashpw(password));
        userMapper.updateById(user);
        StpUtil.logout(id);
    }

    /** 用户修改自己的密码 */
    public void changePassword(String oldPassword, String newPassword) {
        SysUser user = getById(StpUtil.getLoginIdAsLong());
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BizException("原密码错误");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BizException("新密码不能和原密码相同");
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
    }

    public List<Long> getRoleIds(Long userId) {
        getById(userId);
        return userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    /** 给用户分配角色：用户和角色都必须属于当前企业 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        getById(userId);
        Set<Long> ids = roleService.checkRoleIds(roleIds);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        for (Long roleId : ids) {
            userRoleMapper.insert(new SysUserRole(userId, roleId));
        }
    }
}
