package com.safework.system.dto.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminPasswordDTO {
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码 6-32 位")
    private String password;
}
