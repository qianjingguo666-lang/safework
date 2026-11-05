package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位人员 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ppe_post_user")
public class PpePostUser extends BaseEntity {
    private Long postId;
    private Long userId;
    private String userName;
}
