package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 危化品存储单元（罐区、仓库、生产装置）：重大危险源按单元辨识 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chem_store")
public class ChemStore extends BaseEntity {
    private String name;
    /** TANK_AREA 罐区 / WAREHOUSE 库区 / PRODUCTION 生产装置 */
    private String storeType;
    private String location;
    /** 单元外 500 m 范围内常住人数（算 α） */
    private Integer people;
    /** 负责人：能办理这个单元的出入库 */
    private Long managerId;
    private String managerName;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;
}
