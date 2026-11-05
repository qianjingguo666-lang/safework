package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 年度安全目标：一个指标一行（目标值 + 权重） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kpi_target")
public class KpiTarget extends BaseEntity {
    private Integer year;
    private String metricCode;
    private Double targetValue;
    private Integer weight;
    private String updaterName;
}
