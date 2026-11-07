package com.safework.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.safety.entity.PointReward;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointRewardMapper extends BaseMapper<PointReward> {

    /** 库存加减（条件更新，库存不会变成负数） */
    @Update("UPDATE point_reward SET stock = stock + #{delta}, update_time = NOW() WHERE id = #{id} AND stock + #{delta} >= 0 AND deleted = 0")
    int changeStock(@Param("id") Long id, @Param("delta") int delta);
}
