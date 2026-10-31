package com.safework.safety.contractor;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 审核、驳回、拉黑、解除的意见（驳回、拉黑、解除必填） */
@Data
public class ContractorActionDTO {
    @Size(max = 200, message = "意见不超过 200 字")
    private String comment;
}
