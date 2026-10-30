package com.safework.safety.dto;

import com.safework.safety.service.LecEvaluator;

import java.util.List;
import java.util.Map;

/** LEC 标准分值表（前端下拉框用，和后端校验用的是同一份数据） */
public record LecOptionsVO(List<LecEvaluator.Option> l, List<LecEvaluator.Option> e, List<LecEvaluator.Option> c,
                           List<Map<String, Object>> levels) {
}
