package com.safework.safety.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.safework.common.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 设备检验 / 校验 / 检定记录 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("equipment_inspection")
public class EquipmentInspection extends BaseEntity {
    private Long equipmentId;
    private LocalDate inspectDate;
    /** PASS / FAIL */
    private String result;
    /** 检验机构 */
    private String org;
    private String reportNo;
    /** 检验结论、发现的问题 */
    private String conclusion;
    /** 合格时给出的下次检验日期 */
    private LocalDate nextInspectDate;
    /** 不合格转的隐患 */
    private Long hazardId;
    private String hazardNo;
    private String recorderName;
}
