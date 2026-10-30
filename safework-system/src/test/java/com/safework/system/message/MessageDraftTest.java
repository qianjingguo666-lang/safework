package com.safework.system.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("消息内容")
class MessageDraftTest {

    @Test
    @DisplayName("标题、内容超长时截断（数据库字段 100 / 500）")
    void truncates() {
        MessageDraft d = MessageDraft.of(MessageCategory.TODO, "TICKET", 1L, "标".repeat(150), "内".repeat(800), null);
        assertEquals(100, d.title().length());
        assertEquals(500, d.content().length());
        assertTrue(d.title().endsWith("…"));
    }

    @Test
    @DisplayName("短内容和空内容保持原样")
    void keepsShort() {
        MessageDraft d = MessageDraft.of(MessageCategory.NOTICE, null, null, "已批准", null, "/ticket/list?detail=1");
        assertEquals("已批准", d.title());
        assertNull(d.content());
    }
}
