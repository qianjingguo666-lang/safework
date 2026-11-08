package com.safework.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.ticket.entity.RiskCommitment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RiskCommitmentMapper extends BaseMapper<RiskCommitment> {

    /** 签名图片（实体上 select = false）。租户条件由多租户插件加上 */
    @Select("SELECT sign_image FROM risk_commitment WHERE id = #{id} AND deleted = 0")
    String signImage(@Param("id") Long id);
}
