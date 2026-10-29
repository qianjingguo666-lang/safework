package com.safework.system.dto.platform;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/** 修改企业：企业编码不能改（员工登录要用） */
@Data
public class TenantUpdateDTO {
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 100, message = "企业名称最多 100 字")
    private String tenantName;
    @Size(max = 50, message = "联系人最多 50 字")
    private String contactName;
    @Size(max = 20, message = "联系电话最多 20 位")
    private String contactPhone;
    @NotNull(message = "请选择套餐")
    private Long packageId;
    private LocalDateTime expireTime;
    @Min(value = 1, message = "账号上限至少为 1")
    private Integer accountLimit;
    @Size(max = 200, message = "备注最多 200 字")
    private String remark;
}
