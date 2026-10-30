package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import com.safework.safety.enums.RiskLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 风险点巡检记录：逐条确认管控措施是否有效，失效的可以一键转为隐患 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("risk_check")
public class RiskCheck extends BaseEntity {
    private Long pointId;
    private String pointCode;
    private String pointName;
    private RiskLevel riskLevel;
    private Long checkerId;
    private String checkerName;
    private LocalDateTime checkTime;
    /** NORMAL 正常 / ABNORMAL 有措施失效 */
    private String result;
    /** 逐条检查结果（JSON）：[{measure, ok, note}] */
    private String items;
    private String remark;
    /** 转成的隐患 */
    private Long hazardId;
    private String hazardNo;
}
