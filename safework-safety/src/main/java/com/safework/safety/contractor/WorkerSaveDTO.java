package com.safework.safety.contractor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class WorkerSaveDTO {
    @NotBlank(message = "请填写姓名")
    @Size(max = 20, message = "姓名不超过 20 字")
    private String name;
    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;
    @Size(max = 20, message = "工种不超过 20 字")
    private String jobType;
    /** 入厂安全培训日期 */
    private LocalDate trainingDate;
    /** 特种作业证，没有就都不填 */
    private String certType;
    @Size(max = 50, message = "证书编号不超过 50 字")
    private String certNo;
    private LocalDate certExpire;
}
