package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 劳保用品目录：名称、防护部位、使用期限、库存 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ppe_item")
public class PpeItem extends BaseEntity {
    private String name;
    /** HEAD / RESPIRATORY / EYE_FACE / HEARING / HAND / FOOT / BODY / FALL */
    private String category;
    private String spec;
    private String unit;
    /** 使用期限（月） */
    private Integer validMonths;
    private Integer stock;
    /** 安全库存：低于它在统计和大屏里提示补货 */
    private Integer minStock;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private String remark;
}
