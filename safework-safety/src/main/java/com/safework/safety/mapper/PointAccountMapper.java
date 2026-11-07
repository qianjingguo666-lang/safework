package com.safework.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.safety.entity.PointAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointAccountMapper extends BaseMapper<PointAccount> {

    /** 加减余额：扣减时余额不够条件不成立，返回 0（两个人同时兑换也不会扣成负数）。租户条件由多租户插件加上 */
    @Update("UPDATE point_account SET balance = balance + #{delta}, earned = earned + GREATEST(#{delta}, 0), update_time = NOW() "
            + "WHERE id = #{id} AND balance + #{delta} >= 0 AND deleted = 0")
    int change(@Param("id") Long id, @Param("delta") int delta);
}
