package com.safework.ai.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("选取出题原文")
class ExcerptPickerTest {

    private static List<String> chunks() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            list.add("第" + i + "段" + "内容".repeat(45));
        }
        list.set(15, "第15段 动火作业 灭火器 不少于 2 具" + "内容".repeat(40));
        return list;
    }

    @Test
    @DisplayName("全文不超过上限时全部使用")
    void small() {
        assertEquals("甲\n\n乙", ExcerptPicker.pick(List.of("甲", "乙"), null, 100));
    }

    @Test
    @DisplayName("超过上限：在全文里均匀选取，不只取开头；按原文顺序拼接")
    void spread() {
        String s = ExcerptPicker.pick(chunks(), null, 500);
        assertTrue(s.length() <= 520);
        assertTrue(s.contains("第0段"));
        assertTrue(s.contains("第10段") || s.contains("第15段") || s.contains("第12段"), "应该包含后半部分");
        assertTrue(s.indexOf("第0段") < s.lastIndexOf("段"));
    }

    @Test
    @DisplayName("指定出题重点：优先选相关段落")
    void topic() {
        String s = ExcerptPicker.pick(chunks(), "动火 灭火器", 150);
        assertTrue(s.contains("第15段"));
        assertFalse(s.contains("第0段"));
    }
}
