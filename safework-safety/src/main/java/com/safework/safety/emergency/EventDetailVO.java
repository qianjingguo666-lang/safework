package com.safework.safety.emergency;

import com.safework.safety.entity.EmergencyEvent;
import com.safework.safety.entity.EmergencyEventLog;
import com.safework.safety.entity.EmergencyPlan;

import java.util.List;

/**
 * @param plan    已启动的预案（含处置程序），没启动为 null
 * @param team    应急小组成员姓名
 * @param actions 当前用户能做的操作：START / UPDATE / CLOSE
 */
public record EventDetailVO(EmergencyEvent event, List<EmergencyEventLog> logs, List<String> actions,
                            EmergencyPlan plan, List<String> team) {
}
