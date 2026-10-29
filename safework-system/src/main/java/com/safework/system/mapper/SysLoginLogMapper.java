package com.safework.system.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.safework.system.entity.SysLoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {

    /** 平台查看全部企业的登录日志：跳过租户插件（只在平台接口里调用） */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_login_log ${ew.customSqlSegment}")
    IPage<SysLoginLog> selectPageAllTenants(IPage<SysLoginLog> page, @Param(Constants.WRAPPER) Wrapper<SysLoginLog> wrapper);

    /** 平台首页统计：某个时间之后全平台成功 / 失败的登录次数 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) FROM sys_login_log WHERE status = #{status} AND login_time >= #{since}")
    long countAllTenantsSince(@Param("status") int status, @Param("since") LocalDateTime since);
}
