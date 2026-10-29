package com.safework.safety.dto;

import com.safework.safety.entity.Hazard;
import com.safework.safety.entity.HazardLog;
import com.safework.safety.enums.HazardAction;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class HazardDetailVO {
    private Hazard hazard;
    private List<HazardLog> logs;
    /** 当前用户可执行的动作 */
    private List<HazardAction> actions;
}
