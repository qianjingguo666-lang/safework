package com.safework.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ChatRequest {
    @NotBlank(message = "请输入问题")
    @Size(max = 500, message = "问题不超过 500 字")
    private String question;
    /** 最近几轮对话，用于理解“那动火作业呢？”这类追问；服务端只取最后 6 条 */
    private List<ChatMessage> history;
}
