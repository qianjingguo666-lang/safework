package com.safework.ticket.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.ticket.entity.WorkTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WorkTicketMapper extends BaseMapper<WorkTicket> {

    /**
     * 扫纸质作业票上的二维码验真：还没有登录、没有租户上下文。
     * 先跳过租户插件查出这张票属于哪个企业，后面的查询再切换到这个企业，照常被租户隔离。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT tenant_id FROM work_ticket WHERE verify_token = #{token} AND deleted = 0")
    Long selectTenantIdByVerifyToken(@Param("token") String token);
}
