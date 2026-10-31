package com.safework.safety.emergency;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DrillSaveDTO {
    @NotNull(message = "请选择演练的预案")
    private Long planId;
    @NotBlank(message = "请填写演练名称")
    @Size(max = 60, message = "演练名称不超过 60 字")
    private String title;
    /** TABLETOP 桌面推演 / LIVE 实战演练 */
    @NotBlank(message = "请选择演练形式")
    private String drillType;
    @NotNull(message = "请选择计划时间")
    private LocalDateTime planTime;
    @Size(max = 100, message = "地点不超过 100 字")
    private String location;
}
