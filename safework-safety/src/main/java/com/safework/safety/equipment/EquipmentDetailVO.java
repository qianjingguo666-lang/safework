package com.safework.safety.equipment;

import com.safework.safety.entity.Equipment;
import com.safework.safety.entity.EquipmentInspection;

import java.util.List;

public record EquipmentDetailVO(Equipment equipment, List<EquipmentInspection> inspections) {
}
