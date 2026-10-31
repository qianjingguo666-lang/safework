package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 人员资质证书：特种作业操作证、有限空间培训合格证、安全管理人员证等 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("person_cert")
public class PersonCert extends BaseEntity {
    private Long userId;
    private String userName;
    private String deptName;
    /** 证书类型，见 CertRules.CERT_TYPES */
    private String certType;
    private String certNo;
    /** 发证机关 */
    private String issuer;
    private LocalDate issueDate;
    /** 有效期至，空表示长期 */
    private LocalDate expireDate;
    /** 下次复审日期（特种作业操作证每 3 年复审），空表示不需要复审 */
    private LocalDate reviewDate;
    /** 实际可用到哪天：有效期和复审日期取早的，保存时计算，方便按状态筛选和排序 */
    private LocalDate validUntil;
    private String remark;
    private String updaterName;

    /** VALID / EXPIRING / EXPIRED，查询时计算 */
    @TableField(exist = false)
    private String state;
    /** 还剩几天（负数表示已过期几天），长期有效为空 */
    @TableField(exist = false)
    private Long daysLeft;
}
