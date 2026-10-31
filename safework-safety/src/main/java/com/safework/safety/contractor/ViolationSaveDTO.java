package com.safework.safety.contractor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ViolationSaveDTO {
    /** 违章人员，可以不选（单位整体违章，如未按方案施工） */
    private Long workerId;
    @NotBlank(message = "请填写违章情况")
    @Size(max = 300, message = "违章情况不超过 300 字")
    private String description;
    @NotNull(message = "请填写扣分")
    @Min(value = 1, message = "扣分 1 ~ 20 分")
    @Max(value = 20, message = "扣分 1 ~ 20 分")
    private Integer points;
    @NotNull(message = "请填写违章时间")
    private LocalDateTime occurTime;
}
