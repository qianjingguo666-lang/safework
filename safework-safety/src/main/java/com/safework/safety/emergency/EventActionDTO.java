package com.safework.safety.emergency;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 启动响应：planId + 说明；处置记录：content；结束：content 是事后总结 */
@Data
public class EventActionDTO {
    private Long planId;
    @Size(max = 2000, message = "内容不超过 2000 字")
    private String content;
}
