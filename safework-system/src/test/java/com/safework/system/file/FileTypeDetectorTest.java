package com.safework.system.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("按文件内容识别类型")
class FileTypeDetectorTest {

    private static byte[] head(int... v) {
        byte[] b = new byte[Math.max(12, v.length)];
        for (int i = 0; i < v.length; i++) {
            b[i] = (byte) v[i];
        }
        return b;
    }

    @Test
    @DisplayName("图片按文件头识别，和扩展名无关")
    void images() {
        assertEquals("jpg", FileTypeDetector.detect(head(0xFF, 0xD8, 0xFF, 0xE0), "x.png").ext());
        assertEquals("png", FileTypeDetector.detect(head(0x89, 'P', 'N', 'G'), "a.jpg").ext());
        assertEquals("webp", FileTypeDetector.detect(head('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'), "a").ext());
        assertTrue(FileTypeDetector.detect(head(0xFF, 0xD8, 0xFF), "a").image());
    }

    @Test
    @DisplayName("文档：PDF；docx/xlsx 都是 zip，用扩展名区分；老版 doc/xls")
    void documents() {
        assertEquals("pdf", FileTypeDetector.detect(head('%', 'P', 'D', 'F'), "a.pdf").ext());
        assertFalse(FileTypeDetector.detect(head('%', 'P', 'D', 'F'), "a.pdf").image());
        assertEquals("docx", FileTypeDetector.detect(head('P', 'K', 3, 4), "方案.DOCX").ext());
        assertEquals("xls", FileTypeDetector.detect(head(0xD0, 0xCF, 0x11, 0xE0), "a.xls").ext());
    }

    @Test
    @DisplayName("改了扩展名的脚本、网页、jar 包都被拒绝")
    void rejectsDisguised() {
        assertNull(FileTypeDetector.detect("<%@ page %>   ".getBytes(StandardCharsets.UTF_8), "photo.jpg"));
        assertNull(FileTypeDetector.detect("<html><body>".getBytes(StandardCharsets.UTF_8), "a.png"));
        assertNull(FileTypeDetector.detect(head('P', 'K', 3, 4), "evil.jar"));
        assertNull(FileTypeDetector.detect(new byte[2], "a.png"));
    }
}
