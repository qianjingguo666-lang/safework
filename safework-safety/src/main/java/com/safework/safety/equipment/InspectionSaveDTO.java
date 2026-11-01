package com.safework.safety.equipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class InspectionSaveDTO {
    @NotNull(message = "请填写检验日期")
    private LocalDate inspectDate;
    @NotBlank(message = "请选择检验结论")
    @Pattern(regexp = "PASS|FAIL", message = "检验结论只能是合格或不合格")
    private String result;
    @Size(max = 60, message = "检验机构不超过 60 字")
    private String org;
    @Size(max = 50, message = "报告编号不超过 50 字")
    private String reportNo;
    @Size(max = 500, message = "结论说明不超过 500 字")
    private String conclusion;
    /** 合格时可以按检验报告填写下次检验日期，不填按检验周期计算 */
    private LocalDate nextInspectDate;
    /** 不合格时是否同时上报隐患 */
    private Boolean toHazard;
}
