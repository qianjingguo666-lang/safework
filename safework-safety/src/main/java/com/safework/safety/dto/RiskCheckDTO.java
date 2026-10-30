package com.safework.safety.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RiskCheckDTO {
    @Valid
    @NotEmpty(message = "请逐条确认管控措施")
    private List<Item> items;
    @Size(max = 500, message = "备注不超过 500 字")
    private String remark;
    /** 有失效的措施时，是否同时上报隐患 */
    private Boolean toHazard;
    @Size(max = 100, message = "隐患标题不超过 100 字")
    private String hazardTitle;

    @Data
    public static class Item {
        private String measure;
        @NotNull(message = "请确认每条措施是否有效")
        private Boolean ok;
        @Size(max = 200, message = "说明不超过 200 字")
        private String note;
    }
}
