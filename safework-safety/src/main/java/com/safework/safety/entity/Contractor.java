package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 承包商（外包施工、检维修、清洗等单位） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contractor")
public class Contractor extends BaseEntity {
    private String name;
    /** 统一社会信用代码 */
    private String creditCode;
    private String contactName;
    private String contactPhone;
    /** 业务范围，如：防腐保温、储罐清洗 */
    private String businessScope;
    /** 资质证书（安全生产许可证等）有效期 */
    private LocalDate licenseExpire;
    /** 安全生产责任险到期日 */
    private LocalDate insuranceExpire;
    /** PENDING / APPROVED / REJECTED / BLACKLISTED */
    private String status;
    /** 最近一次审核、拉黑、解除的意见 */
    private String statusComment;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String remark;

    /** 近 12 个月的安全评分，查询时计算 */
    @TableField(exist = false)
    private Integer score;
    /** 在场人员数 */
    @TableField(exist = false)
    private Long workerCount;
    /** 有问题的提示，如“资质证书 20 天后到期” */
    @TableField(exist = false)
    private List<String> warnings;
}
