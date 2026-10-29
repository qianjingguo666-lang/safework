package com.safework.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 查询拥有某个角色的正常用户（租户插件会给三张表都加上 tenant_id 条件） */
    @Select("SELECT u.* FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "WHERE r.role_code = #{roleCode} AND r.status = 1 AND r.deleted = 0 " +
            "AND u.status = 1 AND u.deleted = 0 " +
            "ORDER BY u.id")
    List<SysUser> selectByRoleCode(@Param("roleCode") String roleCode);

    /** 平台统计：每个企业的账号数（跳过租户插件，只在平台接口里调用） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id AS tenantId, COUNT(*) AS cnt FROM sys_user WHERE deleted = 0 GROUP BY tenant_id")
    List<Map<String, Object>> countGroupByTenant();

    /** 拥有某个权限码的正常用户（通过角色 → 菜单），企业管理员另外查询。租户插件会给关联的表都加上 tenant_id 条件 */
    @Select("SELECT DISTINCT u.id FROM sys_user u " +
            "JOIN sys_user_role ur ON ur.user_id = u.id " +
            "JOIN sys_role r ON r.id = ur.role_id " +
            "JOIN sys_role_menu rm ON rm.role_id = r.id " +
            "JOIN sys_menu m ON m.id = rm.menu_id " +
            "WHERE m.perms = #{perm} AND m.status = 1 AND m.deleted = 0 " +
            "AND r.status = 1 AND r.deleted = 0 AND u.status = 1 AND u.deleted = 0")
    List<Long> selectIdsByPermission(@Param("perm") String perm);
}
