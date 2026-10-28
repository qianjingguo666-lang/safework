package com.safework.ticket.dto;

import com.safework.ticket.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 新建 / 修改作业票。草稿阶段气体数据可以先不填，提交审批时再校验 */
@Data
public class TicketSaveDTO {
    @NotNull(message = "作业类型不能为空")
    private TicketType ticketType;
    @NotBlank(message = "作业名称不能为空")
    @Size(max = 100, message = "作业名称不超过 100 字")
    private String title;
    @NotBlank(message = "作业地点不能为空")
    private String location;
    private String workContent;
    private String safetyMeasures;
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime planStartTime;
    @NotNull(message = "计划结束时间不能为空")
    private LocalDateTime planEndTime;
    @NotNull(message = "请选择监护人")
    private Long guardianId;

    private BigDecimal gasOxygen;
    private BigDecimal gasLel;
    private BigDecimal gasH2s;
    private LocalDateTime gasCheckTime;
}
