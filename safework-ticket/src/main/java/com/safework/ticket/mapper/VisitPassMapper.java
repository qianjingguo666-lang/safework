package com.safework.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.safework.ticket.entity.VisitPass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface VisitPassMapper extends BaseMapper<VisitPass> {

    /** 签名图片（实体上 select = false，列表不带，详情单独查）。租户条件由多租户插件加上 */
    @Select("SELECT sign_image FROM visit_pass WHERE id = #{id} AND deleted = 0")
    String signImage(@Param("id") Long id);
}
