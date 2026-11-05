package com.safework.safety.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.safety.entity.ChemStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ChemStockMapper extends BaseMapper<ChemStock> {

    /**
     * 按“变动前的库存”条件更新（乐观并发）：两个人同时出库，后提交的那个条件不成立，返回 0，
     * 不会出现库存被扣成负数或者覆盖别人的出库。租户条件由多租户插件自动加上。
     */
    @Update("UPDATE chem_stock SET qty = #{after}, update_time = NOW() WHERE id = #{id} AND qty = #{before} AND deleted = 0")
    int changeQty(@Param("id") Long id, @Param("before") BigDecimal before, @Param("after") BigDecimal after);
}
