package com.safework.safety.equipment;

import java.time.LocalDate;

/**
 * @param nextInspectDate 合格后的下次检验日期
 * @param stopped         不合格，设备已自动停用
 * @param hazardNo        不合格转的隐患编号
 */
public record InspectionResultVO(LocalDate nextInspectDate, boolean stopped, Long hazardId, String hazardNo) {
}
