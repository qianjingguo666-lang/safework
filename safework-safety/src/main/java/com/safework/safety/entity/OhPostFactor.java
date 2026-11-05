package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 岗位接触的危害因素（岗位复用劳保用品模块的 ppe_post） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oh_post_factor")
public class OhPostFactor extends BaseEntity {
    private Long postId;
    private Long factorId;
}
