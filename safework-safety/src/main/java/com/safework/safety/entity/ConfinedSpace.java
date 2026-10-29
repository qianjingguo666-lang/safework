package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 受限空间台账（按租户隔离） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("confined_space")
public class ConfinedSpace extends BaseEntity {

    /**
     * 只读字段：扫码接口没有登录信息，需要从空间本身知道它属于哪个企业。
     * 插入和更新都不写这个字段，tenant_id 仍然由租户插件负责。
     */
    @JsonIgnore
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Long tenantId;

    private String spaceCode;
    private String spaceName;
    /** 储罐、反应釜、污水池、地下有限空间、管道、其他 */
    private String spaceType;
    private String location;
    private Long deptId;
    private Long responsibleId;
    private String responsibleName;
    /** 存在的有毒有害、易燃易爆介质 */
    private String medium;
    /** HIGH / MEDIUM / LOW */
    private String riskLevel;
    /**
     * 二维码令牌：32 位随机串，扫码匿名访问时用它定位空间，不能用自增 ID（可被遍历）。
     * 列表接口不返回，只能通过有修改权限的单独接口获取
     */
    @JsonIgnore
    private String qrToken;
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private String deptName;
}
