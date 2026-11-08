package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 某一天某个方面（生产 / 设备 / 工艺 / 安全）的研判；承诺前可以重新研判（覆盖） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_judgment")
public class RiskJudgment extends BaseEntity {
    private Long commitmentId;
    private String aspect;
    private Long judgerId;
    private String judgerName;
    private LocalDateTime judgeTime;
    private Integer abnormalCount;
    /** 逐项结果 JSON：[{code, ok, note, measure}] */
    private String items;
}
