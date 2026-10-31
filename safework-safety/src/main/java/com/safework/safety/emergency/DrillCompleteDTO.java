package com.safework.safety.emergency;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DrillCompleteDTO {
    @NotNull(message = "请填写实际演练时间")
    private LocalDateTime actualTime;
    @NotNull(message = "请填写参演人数")
    @Min(value = 1, message = "参演人数至少 1 人")
    private Integer participantCount;
    @NotBlank(message = "请填写演练总结")
    @Size(max = 2000, message = "总结不超过 2000 字")
    private String summary;
    @Size(max = 2000, message = "问题不超过 2000 字")
    private String problems;
}
