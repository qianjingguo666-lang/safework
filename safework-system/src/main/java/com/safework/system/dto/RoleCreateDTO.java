package com.safework.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RoleCreateDTO {
    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,31}$", message = "角色编码只能是小写字母、数字、下划线，且以字母开头")
    private String roleCode;
    @NotBlank(message = "角色名称不能为空")
    private String roleName;
    private Integer sort;
    private String remark;
}
