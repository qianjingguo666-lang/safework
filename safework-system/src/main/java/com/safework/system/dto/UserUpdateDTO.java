package com.safework.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 修改用户基本信息（用户名不可修改） */
@Data
public class UserUpdateDTO {
    @NotBlank(message = "昵称不能为空")
    private String nickname;
    private String phone;
    private Long deptId;
}
