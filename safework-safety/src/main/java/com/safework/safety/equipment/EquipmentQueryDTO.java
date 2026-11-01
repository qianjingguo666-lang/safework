package com.safework.safety.equipment;

import lombok.Data;

@Data
public class EquipmentQueryDTO {
    private long current = 1;
    private long size = 10;
    /** 位号、名称、位置、登记证号 */
    private String keyword;
    private String category;
    private String equipType;
    /** 检验状态：OVERDUE / DUE_SOON / NEVER / OK（只看在用的） */
    private String inspectState;
    /** IN_USE / STOPPED / SCRAPPED，不填默认不看报废的 */
    private String status;
    private Long riskPointId;
}
