package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 职业病危害因素：噪声、苯、甲醇、硫化氢、粉尘…… */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oh_factor")
public class OhFactor extends BaseEntity {
    private String name;
    /** CHEMICAL / DUST / PHYSICAL / BIOLOGICAL / RADIATION */
    private String category;
    /** 在岗体检周期（月） */
    private Integer cycleMonths;
    /** 职业接触限值，如 PC-TWA 25 mg/m³ */
    private String limitValue;
    /** 体检重点项目 */
    private String examItems;
    /** 职业禁忌证 */
    private String contraindication;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private String remark;
}
