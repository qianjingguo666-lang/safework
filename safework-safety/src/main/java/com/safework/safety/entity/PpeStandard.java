package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位发放标准：这个岗位每人应配哪种用品、每次发几件 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ppe_standard")
public class PpeStandard extends BaseEntity {
    private Long postId;
    private Long itemId;
    private Integer quantity;
}
