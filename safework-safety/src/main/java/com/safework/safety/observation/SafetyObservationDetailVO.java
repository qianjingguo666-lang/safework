package com.safework.safety.observation;

import com.safework.safety.entity.SafetyObservation;

public record SafetyObservationDetailVO(SafetyObservation observation, boolean canManage, boolean canClose) {
}
