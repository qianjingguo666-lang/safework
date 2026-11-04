package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 安全检查表模板：一组检查项。改模板只影响以后生成的任务，已生成的任务保存的是当时的快照 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("check_template")
public class CheckTemplate extends BaseEntity {
    private String name;
    /** DAILY / SPECIAL / HOLIDAY / SEASONAL / COMPREHENSIVE */
    private String checkType;
    /** 检查项（JSON）：[{content, standard, category}] */
    private String items;
    private Integer itemCount;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private String remark;
}
