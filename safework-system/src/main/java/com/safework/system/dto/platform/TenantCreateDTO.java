package com.safework.system.dto.platform;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 开通企业：企业信息 + 初始管理员账号 */
@Data
public class TenantCreateDTO {
    @NotBlank(message = "企业编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{2,31}$", message = "企业编码 3-32 位，小写字母开头，只能包含小写字母、数字、下划线")
    private String tenantCode;
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 100, message = "企业名称最多 100 字")
    private String tenantName;
    @Size(max = 50, message = "联系人最多 50 字")
    private String contactName;
    @Size(max = 20, message = "联系电话最多 20 位")
    private String contactPhone;
    @NotNull(message = "请选择套餐")
    private Long packageId;
    /** 为空表示不限期 */
    private LocalDateTime expireTime;
    @Min(value = 1, message = "账号上限至少为 1")
    private Integer accountLimit;
    @Size(max = 200, message = "备注最多 200 字")
    private String remark;

    @NotBlank(message = "管理员用户名不能为空")
    @Size(min = 3, max = 32, message = "管理员用户名 3-32 位")
    private String adminUsername;
    @NotBlank(message = "管理员密码不能为空")
    @Size(min = 6, max = 32, message = "管理员密码 6-32 位")
    private String adminPassword;
}
