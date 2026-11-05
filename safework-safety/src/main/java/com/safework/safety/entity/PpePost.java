package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位：一个岗位一套劳保发放标准，岗位下面挂人 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ppe_post")
public class PpePost extends BaseEntity {
    private String name;
    private String remark;
    /** 1 启用 / 0 停用 */
    private Integer status;
}
