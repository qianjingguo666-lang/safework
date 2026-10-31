package com.safework.safety.contractor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ContractorSaveDTO {
    @NotBlank(message = "请填写单位名称")
    @Size(max = 60, message = "单位名称不超过 60 字")
    private String name;
    @NotBlank(message = "请填写统一社会信用代码")
    @Pattern(regexp = "^[0-9A-HJ-NPQRTUWXY]{18}$", message = "统一社会信用代码是 18 位数字或大写字母")
    private String creditCode;
    @NotBlank(message = "请填写联系人")
    @Size(max = 20, message = "联系人不超过 20 字")
    private String contactName;
    @NotBlank(message = "请填写联系电话")
    @Pattern(regexp = "^[0-9-]{7,20}$", message = "联系电话格式不正确")
    private String contactPhone;
    @Size(max = 200, message = "业务范围不超过 200 字")
    private String businessScope;
    @NotNull(message = "请填写资质证书有效期")
    private LocalDate licenseExpire;
    @NotNull(message = "请填写安全生产责任险到期日")
    private LocalDate insuranceExpire;
    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
