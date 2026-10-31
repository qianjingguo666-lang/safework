package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 开票持证要求：某类作业票的作业负责人 / 监护人必须持有哪些证书（每个企业自己配置） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cert_requirement")
public class CertRequirement extends BaseEntity {
    /** CONFINED_SPACE / HOT_WORK / HEIGHT */
    private String ticketType;
    /** APPLICANT 作业负责人 / GUARDIAN 监护人 */
    private String holderRole;
    private String certType;
}
