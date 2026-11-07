package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 积分账户：一人一个（第一次得分时自动建） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("point_account")
public class PointAccount extends BaseEntity {
    private Long userId;
    private String userName;
    /** 当前余额 */
    private Integer balance;
    /** 累计得到的积分（兑换花掉的不减） */
    private Integer earned;
}
