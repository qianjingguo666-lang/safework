package com.safework.safety.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.safety.entity.ConfinedSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConfinedSpaceMapper extends BaseMapper<ConfinedSpace> {

    /**
     * 扫码查询：此时没有登录、没有租户上下文，所以要跳过租户插件。
     * 安全性靠 qr_token 不可猜测来保证，而且只有这一个方法跳过隔离。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM confined_space WHERE qr_token = #{token} AND deleted = 0")
    ConfinedSpace selectByQrToken(@Param("token") String token);
}
