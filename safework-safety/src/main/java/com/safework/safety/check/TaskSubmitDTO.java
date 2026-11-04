package com.safework.safety.check;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TaskSubmitDTO {
    @Valid
    @NotEmpty(message = "请逐项检查")
    private List<Item> items;
    @Size(max = 500, message = "检查总结不超过 500 字")
    private String summary;

    @Data
    public static class Item {
        /** true 合格 / false 不合格 */
        private Boolean ok;
        /** 现场情况说明：不合格又不转隐患时必填 */
        @Size(max = 200, message = "说明不超过 200 字")
        private String note;
        /** 不合格项是否转为隐患 */
        private Boolean toHazard;
        /** 转隐患的等级，默认一般隐患 */
        @Pattern(regexp = "GENERAL|MAJOR", message = "隐患等级只能是 GENERAL、MAJOR")
        private String level;
    }
}
