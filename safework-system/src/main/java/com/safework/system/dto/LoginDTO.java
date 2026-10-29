package com.safework.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "企业编码不能为空")
    @Size(max = 32, message = "企业编码过长")
    private String tenantCode;
    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名过长")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码过长")
    private String password;
    /** 获取验证码时返回的 key */
    private String captchaKey;
    /** 用户输入的验证码答案 */
    private String captchaCode;
}
