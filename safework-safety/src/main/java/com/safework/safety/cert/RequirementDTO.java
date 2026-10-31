package com.safework.safety.cert;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 整体保存开票持证要求（页面上是一个勾选矩阵） */
@Data
public class RequirementDTO {
    @NotNull(message = "要求不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        private String ticketType;
        private String holderRole;
        private String certType;
    }
}
