package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 兑换申请：PENDING 待发放 → ISSUED 已发放 / REJECTED 已退回 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("point_redeem")
public class PointRedeem extends BaseEntity {
    private String redeemNo;
    private Long userId;
    private String userName;
    private Long rewardId;
    private String rewardName;
    private Integer points;
    private BigDecimal price;
    private String status;
    private String handlerName;
    private LocalDateTime handleTime;
    private String remark;
    /** 发放时记入安全投入的支出记录 */
    private Long costExpenseId;
}
