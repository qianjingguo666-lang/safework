package com.safework.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
}
