package com.safework.safety.cert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CertSaveDTO {
    @NotNull(message = "请选择持证人")
    private Long userId;
    @NotBlank(message = "请选择证书类型")
    private String certType;
    @NotBlank(message = "请填写证书编号")
    @Size(max = 50, message = "证书编号不超过 50 字")
    private String certNo;
    @Size(max = 60, message = "发证机关不超过 60 字")
    private String issuer;
    @NotNull(message = "请填写发证日期")
    private LocalDate issueDate;
    /** 空表示长期有效 */
    private LocalDate expireDate;
    /** 下次复审日期，空表示不需要复审 */
    private LocalDate reviewDate;
    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
