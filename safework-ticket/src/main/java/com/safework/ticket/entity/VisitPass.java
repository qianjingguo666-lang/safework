package com.safework.ticket.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 入厂预约：访客 / 普通车辆 / 危化品运输车辆，一次来访一条 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("visit_pass")
public class VisitPass extends BaseEntity {
    private String passNo;
    /** VisitRules.TYPES */
    private String visitType;
    /** 来访人（车辆为驾驶员） */
    private String visitorName;
    private String phone;
    /** 身份证号（打码，只存前 3 后 4） */
    private String idMasked;
    private String company;
    private String purpose;
    private Integer peopleCount;
    private String plateNo;
    private Long hostId;
    private String hostName;
    private Long creatorId;
    private String creatorName;
    private LocalDate planDate;
    private String status;
    // ---------- 危化品车辆 ----------
    private String driverCertNo;
    private LocalDate driverCertExpire;
    private String escortName;
    private String escortCertNo;
    private LocalDate escortCertExpire;
    private String transportPermitNo;
    private LocalDate transportPermitExpire;
    private String cargoName;
    private String cargoUnNo;
    private Double cargoQty;
    private String destination;
    // ---------- 审批、门岗 ----------
    private String approverName;
    private LocalDateTime approveTime;
    private String rejectReason;
    /** 入厂安全须知手写签名（列表不查，详情单独查） */
    @TableField(select = false)
    private String signImage;
    private String signHash;
    private LocalDateTime signTime;
    private LocalDateTime enterTime;
    private String enterGuard;
    private LocalDateTime leaveTime;
    private String leaveGuard;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String remark;
}
