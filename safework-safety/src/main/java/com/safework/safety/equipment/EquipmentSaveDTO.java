package com.safework.safety.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EquipmentSaveDTO {
    @NotBlank(message = "请填写设备位号")
    @Size(max = 30, message = "设备位号不超过 30 字")
    private String equipCode;
    @NotBlank(message = "请填写设备名称")
    @Size(max = 60, message = "设备名称不超过 60 字")
    private String name;
    @NotBlank(message = "请选择设备类型")
    private String equipType;
    @NotBlank(message = "请填写安装位置")
    @Size(max = 100, message = "位置不超过 100 字")
    private String location;
    private Long responsibleId;
    private Long riskPointId;
    @Size(max = 50, message = "使用登记证编号不超过 50 字")
    private String registerNo;
    @Size(max = 60, message = "制造单位不超过 60 字")
    private String manufacturer;
    @Size(max = 60, message = "型号不超过 60 字")
    private String model;
    private LocalDate installDate;
    /** 检验周期（月），不填用设备类型的默认值 */
    private Integer cycleMonths;
    /** 只在登记时填写：已有设备录入上次检验日期，系统据此算下次检验日期 */
    private LocalDate lastInspectDate;
    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
