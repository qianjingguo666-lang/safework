package com.safework.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("从大模型回答里提取 JSON")
class JsonExtractorTest {

    @Test
    @DisplayName("回答前后有说明文字和 Markdown 代码块，字符串里有转义引号和花括号")
    void extractsFirstObject() {
        String llm = "好的，结果如下：\n```json\n{\"riskLevel\":\"HIGH\",\"issues\":[{\"problem\":\"缺少\\\"盲板\\\"{}\"}],\"summary\":\"x\"}\n```\n以上。";
        assertEquals("{\"riskLevel\":\"HIGH\",\"issues\":[{\"problem\":\"缺少\\\"盲板\\\"{}\"}],\"summary\":\"x\"}",
                JsonExtractor.firstObject(llm));
    }

    @Test
    @DisplayName("没有 JSON 时返回 null")
    void noJson() {
        assertNull(JsonExtractor.firstObject("模型没有按要求输出"));
    }
}
