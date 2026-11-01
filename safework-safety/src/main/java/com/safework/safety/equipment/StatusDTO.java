package com.safework.safety.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StatusDTO {
    @NotBlank(message = "请选择状态")
    private String status;
    @Size(max = 200, message = "原因不超过 200 字")
    private String reason;
}
