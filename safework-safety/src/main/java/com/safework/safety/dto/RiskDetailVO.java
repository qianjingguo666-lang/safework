package com.safework.safety.dto;

import com.safework.safety.entity.RiskCheck;
import com.safework.safety.entity.RiskPoint;

import java.util.List;

/** 风险点详情：基本信息、管控措施（逐条）、最近的巡检记录 */
public record RiskDetailVO(RiskPoint point, List<String> measures, List<RiskCheck> checks) {
}
