package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;

/** 承包商作业人员（不是系统用户，不能登录；身份证号等敏感信息不入库） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contractor_worker")
public class ContractorWorker extends BaseEntity {
    private Long contractorId;
    private String name;
    private String phone;
    /** 工种，如：焊工、架子工、清洗工 */
    private String jobType;
    /** 入厂安全培训日期，有效期 12 个月 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate trainingDate;
    /** 特种作业证：类型（同人员资质的证书类型）、编号、有效期，没有就不填 */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String certType;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String certNo;
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate certExpire;
    /** 1 在场 0 离场（停用） */
    private Integer status;

    @TableField(exist = false)
    private LocalDate trainingExpire;
    /** 进场作业的问题（培训过期等），空表示可以作业 */
    @TableField(exist = false)
    private List<String> problems;
}
